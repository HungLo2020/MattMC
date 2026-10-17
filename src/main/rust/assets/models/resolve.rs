//! Model discovery and resolution (`ModelDiscovery`, `ResolvedModel`,
//! `TextureSlots.Resolver`): which models the roots reach, their parents,
//! the models a cycle makes invalid, and each model's inherited
//! properties.

use std::collections::HashMap;
use std::sync::{Arc, OnceLock};

use super::model::{self, Geometry, GuiLight, Model, Slot, TransformSlot};
use crate::content::tags::Location;
use crate::core::java_log;

/// `MissingBlockModel.LOCATION`.
pub(crate) fn missing_location() -> Location {
    Location::new("minecraft", "builtin/missing")
}

/// `ItemModelGenerator.GENERATED_ITEM_MODEL_ID`.
pub(crate) fn generated_location() -> Location {
    Location::new("minecraft", "builtin/generated")
}

/// A transform object: the model that declared it and its index there.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub(crate) enum TransformRef {
    /// `ItemTransform.NO_TRANSFORM`.
    None,
    /// A `null` read from JSON.
    Null,
    Value { model: u32, index: u8 },
}

/// A discovered model (`ModelDiscovery.ModelWrapper`).
pub(crate) struct Wrapper {
    pub(crate) id: Location,
    pub(crate) model: Arc<Model>,
    pub(crate) parent: Option<u32>,
    valid: bool,
    textures: OnceLock<Arc<HashMap<String, Location>>>,
}

/// The discovered models and the id each resolves to.
pub(crate) struct Discovery {
    pub(crate) wrappers: Vec<Wrapper>,
    /// Model id to wrapper (a missing file maps to the missing model).
    by_id: HashMap<Location, u32>,
    queue: std::collections::VecDeque<u32>,
}

/// The missing model's wrapper index.
pub(crate) const MISSING: u32 = 0;

impl Discovery {
    /// `new ModelDiscovery(models, missing)` and `addSpecialModel(builtin/generated)`.
    pub(crate) fn new() -> Discovery {
        let mut discovery = Discovery { wrappers: Vec::new(), by_id: HashMap::new(), queue: Default::default() };
        discovery.push(missing_location(), Arc::new(model::missing()));
        discovery.push(generated_location(), Arc::new(model::generated()));
        discovery
    }

    fn push(&mut self, id: Location, model: Arc<Model>) -> u32 {
        let index = self.wrappers.len() as u32;
        let root = model.parent.is_none();
        self.wrappers.push(Wrapper { id: id.clone(), model, parent: None, valid: root, textures: OnceLock::new() });
        self.by_id.insert(id, index);
        if !root {
            self.queue.push_back(index);
        }
        index
    }

    /// `getOrCreateModel`: a file never found resolves to the missing model,
    /// with one warning.
    pub(crate) fn mark(&mut self, id: &Location, files: &HashMap<Location, Arc<Model>>) -> u32 {
        if let Some(&index) = self.by_id.get(id) {
            return index;
        }
        match files.get(id) {
            Some(model) => self.push(id.clone(), model.clone()),
            None => {
                java_log::warn(&format!("Missing block model: {id}"));
                self.by_id.insert(id.clone(), MISSING);
                MISSING
            }
        }
    }

    /// `resolve()`: parents breadth first, then validity spreads from root
    /// models; models a cycle keeps invalid are dropped with a warning.
    pub(crate) fn resolve(&mut self, files: &HashMap<Location, Arc<Model>>) {
        let mut pending = Vec::new();
        while let Some(index) = self.queue.pop_front() {
            let parent_id = self.wrappers[index as usize].model.parent.clone().expect("queued models have parents");
            let parent = self.mark(&parent_id, files);
            self.wrappers[index as usize].parent = Some(parent);
            if self.wrappers[parent as usize].valid {
                self.wrappers[index as usize].valid = true;
            } else {
                pending.push(index);
            }
        }
        let mut changed = true;
        while changed {
            changed = false;
            pending.retain(|&index| {
                let parent = self.wrappers[index as usize].parent.unwrap();
                if self.wrappers[parent as usize].valid {
                    self.wrappers[index as usize].valid = true;
                    changed = true;
                    false
                } else {
                    true
                }
            });
        }
        let mut invalid: Vec<&Location> = self.by_id.iter().filter(|(_, &index)| !self.wrappers[index as usize].valid).map(|(id, _)| id).collect();
        invalid.sort_by(|a, b| crate::core::id::compare_locations((&a.namespace, &a.path), (&b.namespace, &b.path)));
        for id in invalid {
            java_log::warn(&format!("Model {id} ignored due to cyclic dependency"));
        }
    }

    /// `ModelBakery.ModelBakerImpl.getModel`: a model discovery reached, or
    /// the missing model (warning when the id was never discovered or a
    /// cycle dropped it).
    pub(crate) fn get(&self, id: &Location) -> u32 {
        match self.by_id.get(id) {
            Some(&index) if self.wrappers[index as usize].valid => index,
            _ => {
                java_log::warn(&format!("Requested a model that was not discovered previously: {id}"));
                MISSING
            }
        }
    }

    /// `get(id)` without its warning.
    pub(crate) fn find(&self, id: &Location) -> u32 {
        match self.by_id.get(id) {
            Some(&index) if self.wrappers[index as usize].valid => index,
            _ => MISSING,
        }
    }

    /// Whether `get(id)` finds a model without warning.
    pub(crate) fn discovered(&self, id: &Location) -> bool {
        self.by_id.get(id).is_some_and(|&index| self.wrappers[index as usize].valid)
    }

    /// The model and its ancestors, nearest first.
    fn chain(&self, index: u32) -> impl Iterator<Item = &Wrapper> {
        let mut next = Some(index);
        std::iter::from_fn(move || {
            let wrapper = &self.wrappers[next? as usize];
            next = wrapper.parent;
            Some(wrapper)
        })
    }

    /// `findTopAmbientOcclusion` (default true).
    pub(crate) fn ambient_occlusion(&self, index: u32) -> bool {
        self.chain(index).find_map(|wrapper| wrapper.model.ambient_occlusion).unwrap_or(true)
    }

    /// `findTopGuiLight` (default side).
    pub(crate) fn gui_light(&self, index: u32) -> GuiLight {
        self.chain(index).find_map(|wrapper| wrapper.model.gui_light).unwrap_or(GuiLight::Side)
    }

    /// `findTopGeometry`: `None` is `UnbakedGeometry.EMPTY`.
    pub(crate) fn geometry(&self, index: u32) -> Option<&Geometry> {
        self.chain(index).find_map(|wrapper| wrapper.model.geometry.as_ref())
    }

    /// `findTopTransform` per context: the first transform that is not
    /// `NO_TRANSFORM` (Java compares identity, so a `null` stops the search).
    pub(crate) fn transforms(&self, index: u32) -> [TransformRef; 9] {
        let mut found = [TransformRef::None; 9];
        for (context, slot) in found.iter_mut().enumerate() {
            for wrapper in self.chain(index) {
                let Some(transforms) = &wrapper.model.transforms else { continue };
                match transforms.slots[context] {
                    TransformSlot::None => continue,
                    TransformSlot::Null => *slot = TransformRef::Null,
                    TransformSlot::Value(value) => {
                        let model = self.by_id[&wrapper.id];
                        *slot = TransformRef::Value { model, index: value };
                    }
                }
                break;
            }
        }
        found
    }

    /// `getTopTextureSlots`: slots from the root ancestor down, a value and
    /// a reference of one name replacing each other, then references
    /// resolved until nothing changes (unresolved ones are warned about).
    pub(crate) fn textures(&self, index: u32) -> Arc<HashMap<String, Location>> {
        self.wrappers[index as usize]
            .textures
            .get_or_init(|| {
                let chain: Vec<&Wrapper> = self.chain(index).collect();
                let mut values: Vec<(String, Location)> = Vec::new();
                let mut references: Vec<(String, String)> = Vec::new();
                for wrapper in chain.iter().rev() {
                    for (name, slot) in &wrapper.model.textures {
                        match slot {
                            Slot::Value(location) => {
                                references.retain(|(key, _)| key != name);
                                match values.iter_mut().find(|(key, _)| key == name) {
                                    Some(entry) => entry.1 = location.clone(),
                                    None => values.push((name.clone(), location.clone())),
                                }
                            }
                            Slot::Reference(target) => {
                                values.retain(|(key, _)| key != name);
                                match references.iter_mut().find(|(key, _)| key == name) {
                                    Some(entry) => entry.1 = target.clone(),
                                    None => references.push((name.clone(), target.clone())),
                                }
                            }
                        }
                    }
                }
                let mut changed = !references.is_empty();
                while changed {
                    changed = false;
                    references.retain(|(key, target)| match values.iter().find(|(name, _)| name == target).map(|(_, location)| location.clone()) {
                        Some(location) => {
                            match values.iter_mut().find(|(name, _)| name == key) {
                                Some(entry) => entry.1 = location,
                                None => values.push((key.clone(), location)),
                            }
                            changed = true;
                            false
                        }
                        None => true,
                    });
                }
                if !references.is_empty() {
                    let lines: String = references.iter().map(|(key, target)| format!("\t#{key}-> #{target}\n")).collect();
                    java_log::warn(&format!("Unresolved texture references in {}:\n{lines}", self.wrappers[index as usize].id));
                }
                Arc::new(values.into_iter().collect())
            })
            .clone()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn file(text: &str) -> Arc<Model> {
        Arc::new(model::decode(text.as_bytes()).unwrap())
    }

    #[test]
    fn discovers_and_resolves() {
        let mut files = HashMap::new();
        let location = |path: &str| Location::new("minecraft", path);
        files.insert(location("block/base"), file(r##"{"ambientocclusion": false, "textures": {"particle": "#side", "side": "block/a"}, "elements": []}"##));
        files.insert(location("block/child"), file(r##"{"parent": "block/base", "textures": {"side": "block/b", "top": "#side"}}"##));
        files.insert(location("block/loop_a"), file(r#"{"parent": "block/loop_b"}"#));
        files.insert(location("block/loop_b"), file(r#"{"parent": "block/loop_a"}"#));
        files.insert(location("block/orphan"), file(r#"{"parent": "block/nowhere"}"#));
        let mut discovery = Discovery::new();
        let child = discovery.mark(&location("block/child"), &files);
        discovery.mark(&location("block/loop_a"), &files);
        let orphan = discovery.mark(&location("block/orphan"), &files);
        assert_eq!(MISSING, discovery.mark(&location("block/absent"), &files));
        discovery.resolve(&files);
        assert_eq!(child, discovery.get(&location("block/child")));
        assert_eq!(MISSING, discovery.get(&location("block/loop_a")), "a cycle is dropped");
        assert!(!discovery.ambient_occlusion(child));
        let textures = discovery.textures(child);
        assert_eq!(Some(&location("block/b")), textures.get("particle"));
        assert_eq!(Some(&location("block/b")), textures.get("top"));
        // A missing parent is the missing model: its cube and textures are inherited.
        assert_eq!(Some(MISSING), discovery.wrappers[orphan as usize].parent);
        assert_eq!(Some(&location("missingno")), discovery.textures(orphan).get("particle"));
    }
}
