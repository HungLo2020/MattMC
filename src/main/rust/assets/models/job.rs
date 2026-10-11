//! One model reload (`ModelManager`'s loading, discovery and baking):
//! model files and blockstate stacks load in parallel, roots are
//! discovered, and every geometry the states and item roots need is baked
//! in parallel. Java builds its model objects from the result.

use std::collections::HashMap;
use std::sync::Arc;

use super::bake::{Baker, Matrices, Missing, Quads, Sprites};
use super::blockstate::{self, Definition, Root, States, Unbaked, Variant};
use super::model::{self, GuiLight, Model};
use super::resolve::{Discovery, TransformRef, MISSING};
use crate::assets::resources::manager::{Filters, Manager};
use crate::content::tags::Location;
use crate::core::{java_log, parallel};

/// A state definition Java knows (`BlockStateDefinitions`), by blockstate id.
pub(crate) struct Definitions {
    pub(crate) by_id: HashMap<Location, usize>,
    pub(crate) states: Vec<States>,
    /// The first global state index of each definition.
    pub(crate) offsets: Vec<usize>,
}

/// Loaded files and instantiated blockstates.
pub(crate) struct Loaded {
    pub(crate) files: HashMap<Location, Arc<Model>>,
    pub(crate) roots: Vec<Root>,
    /// Global state index to root.
    pub(crate) state_roots: Vec<Option<u32>>,
    /// Per multipart root, each of its definition's states' selectors.
    pub(crate) selectors: HashMap<u32, (usize, Vec<Vec<u32>>)>,
}

/// `FileToIdConverter(prefix, ".json").fileToId`.
fn file_id(namespace: &str, path: &str, prefix: &str) -> Option<Location> {
    let stripped = path.strip_prefix(prefix)?.strip_prefix('/')?.strip_suffix(".json")?;
    Some(Location::new(namespace, stripped))
}

/// `ModelManager.loadBlockModels` and `BlockStateModelLoader.loadBlockStates`.
pub(crate) fn load(manager: &Manager, filters: &dyn Filters, definitions: &Definitions) -> Loaded {
    // Listing may ask Java's filters, so it runs here; reads run in parallel.
    let model_files: Vec<((String, String), crate::assets::resources::manager::Found)> =
        manager.list("models", filters).into_iter().filter(|((_, path), _)| path.ends_with(".json")).collect();
    let decoded = parallel::map(&model_files, |((namespace, path), found)| {
        let id = file_id(namespace, path, "models")?;
        match manager.read(found.resource, namespace, path).map_err(|error| error.to_string()).and_then(|bytes| model::decode(&bytes)) {
            Ok(model) => Some((id, Arc::new(model))),
            Err(error) => {
                java_log::error(&format!("Failed to load model {namespace}:{path}\n{error}"));
                None
            }
        }
    });
    let files: HashMap<Location, Arc<Model>> = decoded.into_iter().flatten().collect();

    let stacks: Vec<(Location, usize, Vec<(String, crate::assets::resources::manager::Found)>, String, String)> = manager
        .list_stacks("blockstates", filters)
        .into_iter()
        .filter(|((_, path), _)| path.ends_with(".json"))
        .filter_map(|((namespace, path), found)| {
            let id = file_id(&namespace, &path, "blockstates")?;
            let Some(&definition) = definitions.by_id.get(&id) else {
                // `LOGGER.debug("Discovered unknown block state definition {}, ignoring")`.
                return None;
            };
            let sources = found.into_iter().map(|found| (manager.pack_id(found.resource.pack).to_owned(), found)).collect();
            Some((id, definition, sources, namespace, path))
        })
        .collect();
    let instantiated = parallel::map(&stacks, |(id, definition, sources, namespace, path)| {
        let mut parsed: Vec<(String, Definition)> = Vec::new();
        for (pack, found) in sources {
            match manager.read(found.resource, namespace, path).map_err(|error| error.to_string()).and_then(|bytes| blockstate::decode(&bytes)) {
                Ok(definition) => parsed.push((pack.clone(), definition)),
                Err(error) => java_log::error(&format!("Failed to load blockstate definition {id} from pack {pack}\n{error}")),
            }
        }
        match blockstate::instantiate(&definitions.states[*definition], id, &parsed) {
            Ok(instantiated) => Some((*definition, instantiated)),
            Err(error) => {
                java_log::error(&format!("Failed to load blockstate definition {id}\n{error}"));
                None
            }
        }
    });
    let total_states: usize = definitions.states.iter().map(|states| states.states.len()).sum();
    let mut loaded = Loaded { files, roots: Vec::new(), state_roots: vec![None; total_states], selectors: HashMap::new() };
    for (definition, instantiated) in instantiated.into_iter().flatten() {
        let base = loaded.roots.len() as u32;
        loaded.roots.extend(instantiated.roots);
        let offset = definitions.offsets[definition];
        for (state, root) in instantiated.state_roots.into_iter().enumerate() {
            loaded.state_roots[offset + state] = root.map(|root| base + root);
        }
        for (root, selectors) in instantiated.selectors {
            loaded.selectors.insert(base + root, (definition, selectors));
        }
    }
    loaded
}

/// `ModelGroupCollector`'s equality groups: one per simple root, one per
/// multipart root and set of matching selectors; -1 for states without a
/// root.
pub(crate) fn groups(loaded: &Loaded, definitions: &Definitions) -> Vec<i32> {
    let mut keys: HashMap<(u32, Vec<u32>), i32> = HashMap::new();
    let mut groups = vec![-1; loaded.state_roots.len()];
    for (definition, states) in definitions.states.iter().enumerate() {
        let offset = definitions.offsets[definition];
        for state in 0..states.states.len() {
            let Some(root) = loaded.state_roots[offset + state] else { continue };
            let selectors = match loaded.selectors.get(&root) {
                Some((_, selectors)) => selectors[state].clone(),
                None => Vec::new(),
            };
            let next = keys.len() as i32;
            groups[offset + state] = *keys.entry((root, selectors)).or_insert(next);
        }
    }
    groups
}

/// The roots' and items' models discovered.
pub(crate) fn discover(loaded: &Loaded, item_roots: &[Location]) -> Discovery {
    let mut discovery = Discovery::new();
    let mut used: Vec<u32> = loaded.state_roots.iter().flatten().copied().collect();
    used.sort_unstable();
    used.dedup();
    for root in used {
        let unbaked: Vec<&Unbaked> = match &loaded.roots[root as usize] {
            Root::Simple(unbaked) => vec![unbaked],
            Root::Multipart(selectors) => selectors.iter().collect(),
        };
        for unbaked in unbaked {
            for variant in unbaked.variants() {
                discovery.mark(&variant.model, &loaded.files);
            }
        }
    }
    for id in item_roots {
        discovery.mark(id, &loaded.files);
    }
    discovery.resolve(&loaded.files);
    discovery
}

/// Distinct element rotations (axis, angle bits) the discovered models use.
pub(crate) fn element_rotations(discovery: &Discovery) -> Vec<(u32, u32)> {
    let mut rotations: Vec<(u32, u32)> = Vec::new();
    for wrapper in &discovery.wrappers {
        if let Some(model::Geometry::Elements(elements)) = &wrapper.model.geometry {
            for element in elements.iter().flatten() {
                if let Some(rotation) = &element.rotation {
                    let key = (rotation.axis.index(), rotation.angle.to_bits());
                    if !rotations.contains(&key) {
                        rotations.push(key);
                    }
                }
            }
        }
    }
    rotations
}

/// A baked part (`SimpleModelWrapper`).
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Part {
    pub(crate) model: u32,
    pub(crate) geometry: u32,
    pub(crate) ambient_occlusion: bool,
    pub(crate) particle: u32,
}

/// A baked `BlockStateModel`: one part (`SingleVariant`), or weighted
/// single variants.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Node {
    Single(u32),
    Weighted(Vec<(u32, i32)>),
}

/// A baked root: its model (simple) or selectors' models (multipart), or
/// the failure Java logs per state.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum BakedRoot {
    Simple(Node),
    Multipart(Vec<Node>),
    Failed(String),
}

/// A model's top properties for item models and the missing model.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Properties {
    pub(crate) geometry: u32,
    pub(crate) ambient_occlusion: bool,
    pub(crate) gui_light: GuiLight,
    pub(crate) particle: u32,
    pub(crate) transforms: [TransformRef; 9],
}

/// An item root: its model's properties (and the failure baking its
/// geometry hit, which Java reports when an item bakes it), or a model
/// discovery never reached (Java warns per request and uses the missing
/// model).
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum ItemRoot {
    Baked(Properties, Option<String>),
    Undiscovered,
}

/// An item root's missing-sprite reports: baking its geometry, and
/// resolving its particle. Java merges each when an item model does it.
#[derive(Debug, Default)]
pub(crate) struct ItemReports {
    pub(crate) geometry: Missing,
    pub(crate) particle: Missing,
}

/// The bake result.
pub(crate) struct Baked {
    pub(crate) geometries: Vec<Quads>,
    pub(crate) parts: Vec<Part>,
    pub(crate) roots: Vec<Option<BakedRoot>>,
    pub(crate) missing: Properties,
    pub(crate) items: Vec<ItemRoot>,
    /// What block states and the missing model reported, as Java's block
    /// bake evaluates them: each root's parts in order, particle then
    /// geometry, up to the first failure.
    pub(crate) reports: Missing,
    /// Per item root (empty for undiscovered ones).
    pub(crate) item_reports: Vec<ItemReports>,
}

/// A geometry to bake: model, rotation ordinal, UV lock.
type GeometryKey = (u32, usize, bool);

struct Plan {
    keys: Vec<GeometryKey>,
    by_key: HashMap<GeometryKey, u32>,
}

impl Plan {
    fn geometry(&mut self, key: GeometryKey) -> u32 {
        if let Some(&index) = self.by_key.get(&key) {
            return index;
        }
        let index = self.keys.len() as u32;
        self.keys.push(key);
        self.by_key.insert(key, index);
        index
    }
}

/// `resolveParticleSprite` per model, with what each reported.
struct Particles<'a> {
    discovery: &'a Discovery,
    sprites: &'a Sprites,
    matrices: &'a Matrices,
    resolved: HashMap<u32, (u32, Missing)>,
}

impl Particles<'_> {
    /// The `particle` slot's sprite (never a failure: the name is not empty).
    fn get(&mut self, model: u32) -> u32 {
        if let Some((sprite, _)) = self.resolved.get(&model) {
            return *sprite;
        }
        let reports = Missing::default();
        let baker = Baker { sprites: self.sprites, matrices: self.matrices, missing: &reports };
        let textures = self.discovery.textures(model);
        let sprite = baker.resolve(&textures, "particle", &self.discovery.wrappers[model as usize].id.to_string()).unwrap_or(self.sprites.missing);
        self.resolved.insert(model, (sprite, reports));
        sprite
    }

    fn reports(&self, model: u32) -> &Missing {
        &self.resolved[&model].1
    }
}

/// Bakes variants into parts (`SimpleModelWrapper.bake`), planning their geometries.
struct PartBaker<'a, 'b> {
    discovery: &'a Discovery,
    particles: &'a mut Particles<'b>,
    parts: Vec<Part>,
}

impl PartBaker<'_, '_> {
    fn part(&mut self, variant: &Variant, plan: &mut Plan) -> u32 {
        let model = self.discovery.get(&variant.model);
        let ambient_occlusion = self.discovery.ambient_occlusion(model);
        let particle = self.particles.get(model);
        let rotation = variant.x.0 as usize * 4 + variant.y.0 as usize;
        let geometry = plan.geometry((model, rotation, variant.uv_lock));
        self.parts.push(Part { model, geometry, ambient_occlusion, particle });
        (self.parts.len() - 1) as u32
    }

    fn node(&mut self, unbaked: &Unbaked, plan: &mut Plan) -> Node {
        match unbaked {
            Unbaked::Single(variant) => Node::Single(self.part(variant, plan)),
            Unbaked::Weighted(entries) => Node::Weighted(entries.iter().map(|(variant, weight)| (self.part(variant, plan), *weight)).collect()),
        }
    }
}

/// `ModelBakery.bakeModels` for every used root and item root.
pub(crate) fn bake(loaded: &Loaded, discovery: &Discovery, item_roots: &[Location], sprites: &Sprites, matrices: &Matrices) -> Baked {
    let mut plan = Plan { keys: Vec::new(), by_key: HashMap::new() };
    let mut particles = Particles { discovery, sprites, matrices, resolved: HashMap::new() };

    // Parts and particles resolve serially, geometry bakes in parallel.
    let missing_geometry = plan.geometry((MISSING, 0, false));
    let missing = Properties {
        geometry: missing_geometry,
        ambient_occlusion: discovery.ambient_occlusion(MISSING),
        gui_light: discovery.gui_light(MISSING),
        particle: particles.get(MISSING),
        transforms: discovery.transforms(MISSING),
    };

    let mut used = vec![false; loaded.roots.len()];
    for root in loaded.state_roots.iter().flatten() {
        used[*root as usize] = true;
    }
    // Resolve the textures planning reads (parts' and item roots' models)
    // in parallel first; resolving warns of unresolved references, for the
    // same models either way.
    let mut models: Vec<u32> = vec![MISSING];
    for (root, _) in loaded.roots.iter().zip(&used).filter(|(_, used)| **used) {
        let nodes: Vec<&Unbaked> = match root {
            Root::Simple(unbaked) => vec![unbaked],
            Root::Multipart(selectors) => selectors.iter().collect(),
        };
        models.extend(nodes.into_iter().flat_map(Unbaked::variants).map(|variant| discovery.find(&variant.model)));
    }
    models.extend(item_roots.iter().filter(|id| discovery.discovered(id)).map(|id| discovery.find(id)));
    models.sort_unstable();
    models.dedup();
    parallel::map(&models, |&model| {
        discovery.textures(model);
    });

    let mut parts = PartBaker { discovery, particles: &mut particles, parts: Vec::new() };
    let mut roots: Vec<Option<BakedRoot>> = vec![None; loaded.roots.len()];
    let mut root_parts: Vec<std::ops::Range<usize>> = vec![0..0; loaded.roots.len()];
    for (index, root) in loaded.roots.iter().enumerate() {
        if !used[index] {
            continue;
        }
        let first_part = parts.parts.len();
        let baked = match root {
            Root::Simple(unbaked) => BakedRoot::Simple(parts.node(unbaked, &mut plan)),
            Root::Multipart(selectors) => BakedRoot::Multipart(selectors.iter().map(|unbaked| parts.node(unbaked, &mut plan)).collect()),
        };
        root_parts[index] = first_part..parts.parts.len();
        roots[index] = Some(baked);
    }
    let parts = parts.parts;

    let mut items: Vec<ItemRoot> = Vec::with_capacity(item_roots.len());
    let mut item_models: Vec<Option<(u32, u32)>> = Vec::with_capacity(item_roots.len());
    for id in item_roots {
        if !discovery.discovered(id) {
            items.push(ItemRoot::Undiscovered);
            item_models.push(None);
            continue;
        }
        let model = discovery.get(id);
        let geometry = plan.geometry((model, 0, false));
        let properties = Properties {
            geometry,
            ambient_occlusion: discovery.ambient_occlusion(model),
            gui_light: discovery.gui_light(model),
            particle: particles.get(model),
            transforms: discovery.transforms(model),
        };
        item_models.push(Some((model, geometry)));
        items.push(ItemRoot::Baked(properties, None));
    }

    let keys = std::mem::take(&mut plan.keys);
    let baked: Vec<(Result<Quads, String>, Missing)> = parallel::map(&keys, |&(model, rotation, uv_lock)| {
        let reports = Missing::default();
        let baker = Baker { sprites, matrices, missing: &reports };
        let textures = discovery.textures(model);
        let debug = discovery.wrappers[model as usize].id.to_string();
        (baker.geometry(discovery.geometry(model), &textures, rotation, uv_lock, &debug), reports)
    });
    let mut geometries = Vec::with_capacity(baked.len());
    let mut failures: Vec<Option<String>> = Vec::with_capacity(baked.len());
    let mut geometry_reports: Vec<Missing> = Vec::with_capacity(baked.len());
    for (result, reports) in baked {
        match result {
            Ok(quads) => {
                geometries.push(quads);
                failures.push(None);
            }
            Err(failure) => {
                geometries.push(Quads::default());
                failures.push(Some(failure));
            }
        }
        geometry_reports.push(reports);
    }

    // `MissingModels.bake`: geometry, then particle.
    let reports = Missing::default();
    reports.merge(&geometry_reports[missing_geometry as usize]);
    reports.merge(particles.reports(MISSING));
    // Each state's root bakes its parts in order (particle, then geometry)
    // until one fails, which fails the root.
    for (index, root) in roots.iter_mut().enumerate() {
        if root.is_none() {
            continue;
        }
        for part in &parts[root_parts[index].clone()] {
            reports.merge(particles.reports(part.model));
            reports.merge(&geometry_reports[part.geometry as usize]);
            if let Some(failure) = &failures[part.geometry as usize] {
                *root = Some(BakedRoot::Failed(failure.clone()));
                break;
            }
        }
    }
    let mut item_reports: Vec<ItemReports> = Vec::with_capacity(items.len());
    for (item, models) in items.iter_mut().zip(&item_models) {
        let entry = ItemReports::default();
        if let (ItemRoot::Baked(_, slot), Some((model, geometry))) = (item, models) {
            *slot = failures[*geometry as usize].clone();
            entry.geometry.merge(&geometry_reports[*geometry as usize]);
            entry.particle.merge(particles.reports(*model));
        }
        item_reports.push(entry);
    }
    Baked { geometries, parts, roots, missing, items, reports, item_reports }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn file_ids() {
        assert_eq!(Some(Location::new("minecraft", "block/stone")), file_id("minecraft", "models/block/stone.json", "models"));
        assert_eq!(None, file_id("minecraft", "models/block/stone.png", "models"));
    }
}
