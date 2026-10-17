//! Block and item model files (`models/*.json`), decoded as `BlockModel`'s
//! Gson deserializers do: `GsonHelper` conversions (a primitive reads as a
//! string, `Boolean.parseBoolean` of text, floats only from numbers), a
//! `null` element, face or transform read as Java `null`, and Java's
//! messages for rejected files.

use super::geometry::{Axis, Direction, Quadrant};
use crate::content::tags::Location;
use crate::core::json::{self, Json, Object};

/// `UnbakedModel.GuiLight`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum GuiLight {
    Front,
    Side,
}

/// `BlockElementRotation` (origin already in block units).
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct ElementRotation {
    pub(crate) origin: [f32; 3],
    pub(crate) axis: Axis,
    pub(crate) angle: f32,
    pub(crate) rescale: bool,
}

/// `BlockElementFace`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Face {
    pub(crate) cull: Option<Direction>,
    pub(crate) tint: i32,
    pub(crate) texture: String,
    /// min u, min v, max u, max v.
    pub(crate) uvs: Option<[f32; 4]>,
    pub(crate) rotation: Quadrant,
}

/// `BlockElement`: faces in `Direction` order (an `EnumMap`); a face read
/// as `null` is `Some(None)`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Element {
    pub(crate) from: [f32; 3],
    pub(crate) to: [f32; 3],
    pub(crate) faces: [Option<Option<Face>>; 6],
    pub(crate) rotation: Option<ElementRotation>,
    pub(crate) shade: bool,
    pub(crate) light_emission: i32,
}

/// `UnbakedGeometry`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum Geometry {
    /// `SimpleUnbakedGeometry`; `None` for an element read as `null`.
    Elements(Vec<Option<Element>>),
    /// `ItemModelGenerator` (`builtin/generated`).
    Generated,
}

/// `ItemTransform` values (translation and scale already scaled and clamped).
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct Transform {
    pub(crate) rotation: [f32; 3],
    pub(crate) translation: [f32; 3],
    pub(crate) scale: [f32; 3],
}

/// One display context's transform as Java holds it: `NO_TRANSFORM`, a
/// `null` read from JSON, or an object; objects are numbered by the model
/// that declared them (Java shares the instance, notably for a left hand
/// defaulting to the right hand).
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum TransformSlot {
    None,
    Null,
    Value(u8),
}

/// `ItemDisplayContext` order of `ItemTransforms`' fields.
pub(crate) const CONTEXTS: [&str; 9] =
    ["thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand", "head", "gui", "ground", "fixed", "on_shelf"];

/// `ItemTransforms`: slots per context, and the transform objects.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Transforms {
    pub(crate) slots: [TransformSlot; 9],
    pub(crate) values: Vec<Transform>,
}

/// A texture slot's contents (`TextureSlots.SlotContents`).
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Slot {
    /// A texture in the blocks atlas.
    Value(Location),
    /// `#name`: another slot.
    Reference(String),
}

/// `UnbakedModel`.
#[derive(Clone, Debug, PartialEq, Default)]
pub(crate) struct Model {
    pub(crate) geometry: Option<Geometry>,
    pub(crate) gui_light: Option<GuiLight>,
    pub(crate) ambient_occlusion: Option<bool>,
    pub(crate) transforms: Option<Transforms>,
    pub(crate) textures: Vec<(String, Slot)>,
    pub(crate) parent: Option<Location>,
}

/// `GsonHelper.getType`'s description, for messages.
fn kind(value: &Json) -> &'static str {
    match value {
        Json::Null => "null (json)",
        Json::Array(_) => "an array",
        Json::Object(_) => "an object",
        Json::Bool(_) => "a boolean",
        Json::Number(_) => "a number",
        Json::String(_) => "a string",
    }
}

/// `jsonElement.getAsJsonObject()`.
fn as_object<'a>(value: &'a Json, what: &str) -> Result<&'a Object, String> {
    value.as_object().ok_or_else(|| format!("Not a JSON Object: {value} ({what})"))
}

/// `GsonHelper.getAsJsonObject(object, key)`.
fn object_field<'a>(object: &'a Object, key: &str) -> Result<&'a Object, String> {
    match object.get(key) {
        Some(Json::Object(value)) => Ok(value),
        Some(other) => Err(format!("Expected {key} to be a JsonObject, was {}", kind(other))),
        None => Err(format!("Missing {key}, expected to find a JsonObject")),
    }
}

/// `GsonHelper.getAsJsonArray(object, key)`.
fn array_field<'a>(object: &'a Object, key: &str) -> Result<&'a [Json], String> {
    match object.get(key) {
        Some(Json::Array(values)) => Ok(values),
        Some(other) => Err(format!("Expected {key} to be a JsonArray, was {}", kind(other))),
        None => Err(format!("Missing {key}, expected to find a JsonArray")),
    }
}

/// `GsonHelper.convertToString`.
fn to_string(value: &Json, key: &str) -> Result<String, String> {
    match value {
        Json::Bool(_) | Json::Number(_) | Json::String(_) => Ok(value.as_text().unwrap_or_default()),
        other => Err(format!("Expected {key} to be a string, was {}", kind(other))),
    }
}

/// `GsonHelper.getAsString(object, key[, default])`.
fn string_field(object: &Object, key: &str, default: Option<&str>) -> Result<String, String> {
    match (object.get(key), default) {
        (Some(value), _) => to_string(value, key),
        (None, Some(default)) => Ok(default.to_owned()),
        (None, None) => Err(format!("Missing {key}, expected to find a string")),
    }
}

/// `GsonHelper.convertToBoolean`: a primitive's `getAsBoolean`.
fn to_boolean(value: &Json, key: &str) -> Result<bool, String> {
    match value {
        Json::Bool(_) | Json::Number(_) | Json::String(_) => Ok(value.as_java_boolean().unwrap_or(false)),
        other => Err(format!("Expected {key} to be a Boolean, was {}", kind(other))),
    }
}

fn boolean_field(object: &Object, key: &str, default: Option<bool>) -> Result<bool, String> {
    match (object.get(key), default) {
        (Some(value), _) => to_boolean(value, key),
        (None, Some(default)) => Ok(default),
        (None, None) => Err(format!("Missing {key}, expected to find a Boolean")),
    }
}

/// `GsonHelper.convertToFloat`: numbers only, by `Float.parseFloat`.
fn to_float(value: &Json, key: &str) -> Result<f32, String> {
    match value {
        Json::Number(text) => Ok(json::parse_f32(text)),
        other => Err(format!("Expected {key} to be a Float, was {}", kind(other))),
    }
}

fn float_field(object: &Object, key: &str) -> Result<f32, String> {
    match object.get(key) {
        Some(value) => to_float(value, key),
        None => Err(format!("Missing {key}, expected to find a Float")),
    }
}

/// `GsonHelper.convertToInt`: numbers only (`intValue`).
fn to_int(value: &Json, key: &str) -> Result<i32, String> {
    match value {
        Json::Number(text) => Ok(json::int_value(text)),
        other => Err(format!("Expected {key} to be a Int, was {}", kind(other))),
    }
}

fn int_field(object: &Object, key: &str, default: i32) -> Result<i32, String> {
    object.get(key).map_or(Ok(default), |value| to_int(value, key))
}

/// `getVector3f(object, key)`.
fn vector(object: &Object, key: &str) -> Result<[f32; 3], String> {
    let values = array_field(object, key)?;
    if values.len() != 3 {
        return Err(format!("Expected 3 {key} values, found: {}", values.len()));
    }
    let mut vector = [0.0f32; 3];
    for (index, value) in values.iter().enumerate() {
        vector[index] = to_float(value, &format!("{key}[{index}]"))?;
    }
    Ok(vector)
}

fn in_bounds(vector: [f32; 3]) -> bool {
    // `!(v < -16)` and `!(v > 32)`: NaN passes.
    vector.iter().all(|&value| !(value < -16.0) && !(value > 32.0))
}

fn face(value: &Json) -> Result<Option<Face>, String> {
    if value.is_null() {
        return Ok(None);
    }
    let object = as_object(value, "face")?;
    let cull = Direction::by_name(&string_field(object, "cullface", Some(""))?);
    let tint = int_field(object, "tintindex", -1)?;
    let texture = string_field(object, "texture", None)?;
    let uvs = if object.has("uv") {
        let values = array_field(object, "uv")?;
        if values.len() != 4 {
            return Err(format!("Expected 4 uv values, found: {}", values.len()));
        }
        Some([to_float(&values[0], "minU")?, to_float(&values[1], "minV")?, to_float(&values[2], "maxU")?, to_float(&values[3], "maxV")?])
    } else {
        None
    };
    let degrees = int_field(object, "rotation", 0)?;
    let rotation = Quadrant::from_degrees(degrees).ok_or_else(|| format!("Invalid rotation {degrees} found, only 0/90/180/270 allowed"))?;
    Ok(Some(Face { cull, tint, texture, uvs, rotation }))
}

fn element(value: &Json) -> Result<Option<Element>, String> {
    if value.is_null() {
        return Ok(None);
    }
    let object = as_object(value, "element")?;
    let from = vector(object, "from")?;
    if !in_bounds(from) {
        return Err("'from' specifier exceeds the allowed boundaries".into());
    }
    let to = vector(object, "to")?;
    if !in_bounds(to) {
        return Err("'to' specifier exceeds the allowed boundaries".into());
    }
    let rotation = if object.has("rotation") {
        let rotation = object_field(object, "rotation")?;
        let origin = vector(rotation, "origin")?;
        let origin = [origin[0] * 0.0625, origin[1] * 0.0625, origin[2] * 0.0625];
        let axis_name = string_field(rotation, "axis", None)?;
        let axis = Axis::by_name(&axis_name.to_lowercase()).ok_or_else(|| format!("Invalid rotation axis: {axis_name}"))?;
        let angle = float_field(rotation, "angle")?;
        if angle.abs() > 45.0 {
            return Err(format!("Invalid rotation {angle} found, only values in [-45,45] range allowed"));
        }
        let rescale = boolean_field(rotation, "rescale", Some(false))?;
        Some(ElementRotation { origin, axis, angle, rescale })
    } else {
        None
    };
    let mut faces: [Option<Option<Face>>; 6] = Default::default();
    for (name, value) in object_field(object, "faces")?.iter() {
        let direction = Direction::by_name(name).ok_or_else(|| format!("Unknown facing: {name}"))?;
        faces[direction.index()] = Some(face(value)?);
    }
    if faces.iter().all(Option::is_none) {
        return Err("Expected between 1 and 6 unique faces, got 0".into());
    }
    if object.has("shade") && !matches!(object.get("shade"), Some(Json::Bool(_))) {
        return Err("Expected shade to be a Boolean".into());
    }
    let shade = boolean_field(object, "shade", Some(true))?;
    let mut light_emission = 0;
    if object.has("light_emission") {
        let number = matches!(object.get("light_emission"), Some(Json::Number(_)));
        if number {
            light_emission = int_field(object, "light_emission", 0)?;
        }
        if !number || !(0..=15).contains(&light_emission) {
            return Err("Expected light_emission to be an Integer between (inclusive) 0 and 15".into());
        }
    }
    Ok(Some(Element { from, to, faces, rotation, shade, light_emission }))
}

/// `ItemTransform.Deserializer`.
fn transform(value: &Json) -> Result<Transform, String> {
    let object = as_object(value, "transform")?;
    let optional = |key: &str, default: [f32; 3]| if object.has(key) { vector(object, key) } else { Ok(default) };
    let rotation = optional("rotation", [0.0; 3])?;
    let translation = optional("translation", [0.0; 3])?;
    let clamp = |value: f32, limit: f32| clamp(value, -limit, limit);
    let translation = [translation[0] * 0.0625, translation[1] * 0.0625, translation[2] * 0.0625].map(|value| clamp(value, 5.0));
    let scale = optional("scale", [1.0; 3])?.map(|value| clamp(value, 4.0));
    Ok(Transform { rotation, translation, scale })
}

/// Java's `Math.min(float, float)`: NaN wins, and -0.0 is below 0.0.
fn java_min(a: f32, b: f32) -> f32 {
    if a.is_nan() {
        return a;
    }
    if a == 0.0 && b == 0.0 && b.to_bits() == (-0.0f32).to_bits() {
        return b;
    }
    if a <= b {
        a
    } else {
        b
    }
}

/// `Mth.clamp(float, float, float)`.
fn clamp(value: f32, min: f32, max: f32) -> f32 {
    if value < min {
        min
    } else {
        java_min(value, max)
    }
}

/// `ItemTransforms.Deserializer`.
fn transforms(object: &Object) -> Result<Transforms, String> {
    let mut values = Vec::new();
    let mut read = |context: &str| -> Result<TransformSlot, String> {
        match object.get(context) {
            None => Ok(TransformSlot::None),
            Some(Json::Null) => Ok(TransformSlot::Null),
            Some(value) => {
                values.push(transform(value)?);
                Ok(TransformSlot::Value((values.len() - 1) as u8))
            }
        }
    };
    // Read in Java's order: right hands before left ones.
    let third_right = read(CONTEXTS[1])?;
    let mut third_left = read(CONTEXTS[0])?;
    if third_left == TransformSlot::None {
        third_left = third_right;
    }
    let first_right = read(CONTEXTS[3])?;
    let mut first_left = read(CONTEXTS[2])?;
    if first_left == TransformSlot::None {
        first_left = first_right;
    }
    let head = read(CONTEXTS[4])?;
    let gui = read(CONTEXTS[5])?;
    let ground = read(CONTEXTS[6])?;
    let fixed = read(CONTEXTS[7])?;
    let on_shelf = read(CONTEXTS[8])?;
    Ok(Transforms { slots: [third_left, third_right, first_left, first_right, head, gui, ground, fixed, on_shelf], values })
}

/// `TextureSlots.parseTextureMap` (each value's `getAsString`).
fn texture_map(object: &Object) -> Result<Vec<(String, Slot)>, String> {
    let mut slots: Vec<(String, Slot)> = Vec::new();
    for (name, value) in object.iter() {
        let text = value.as_text().ok_or_else(|| format!("{} cannot be read as a string", kind(value)))?;
        let slot = match text.strip_prefix('#') {
            Some(reference) => Slot::Reference(reference.to_owned()),
            None if text.is_empty() => return Err("Index 0 out of bounds for length 0".into()),
            None => Slot::Value(Location::parse(&text).map_err(|_| format!("{text} is not valid resource location"))?),
        };
        slots.push((name.to_owned(), slot));
    }
    Ok(slots)
}

/// `BlockModel.fromStream`: `GsonHelper.fromJson` (strict, the first value).
pub(crate) fn decode(bytes: &[u8]) -> Result<Model, String> {
    let value = json::parse_value(bytes).map_err(|error| error.to_string())?;
    if value.is_null() {
        return Err("JSON data was null or empty".into());
    }
    let object = as_object(&value, "model")?;
    let geometry = if object.has("elements") {
        Some(Geometry::Elements(array_field(object, "elements")?.iter().map(element).collect::<Result<_, _>>()?))
    } else {
        None
    };
    let parent_name = string_field(object, "parent", Some(""))?;
    let textures = if object.has("textures") { texture_map(object_field(object, "textures")?)? } else { Vec::new() };
    let ambient_occlusion = if object.has("ambientocclusion") { Some(boolean_field(object, "ambientocclusion", None)?) } else { None };
    let transforms = if object.has("display") { Some(transforms(object_field(object, "display")?)?) } else { None };
    let gui_light = if object.has("gui_light") {
        let name = string_field(object, "gui_light", None)?;
        Some(match name.as_str() {
            "front" => GuiLight::Front,
            "side" => GuiLight::Side,
            _ => return Err(format!("Invalid gui light: {name}")),
        })
    } else {
        None
    };
    let parent = if parent_name.is_empty() { None } else { Some(Location::parse(&parent_name)?) };
    Ok(Model { geometry, gui_light, ambient_occlusion, transforms, textures, parent })
}

/// `MissingBlockModel.missingModel()`: a full cube of `#missingno`.
pub(crate) fn missing() -> Model {
    let mut faces: [Option<Option<Face>>; 6] = Default::default();
    for direction in Direction::ALL {
        faces[direction.index()] =
            Some(Some(Face { cull: Some(direction), tint: -1, texture: "missingno".into(), uvs: Some([0.0, 0.0, 16.0, 16.0]), rotation: Quadrant(0) }));
    }
    Model {
        geometry: Some(Geometry::Elements(vec![Some(Element {
            from: [0.0; 3],
            to: [16.0; 3],
            faces,
            rotation: None,
            shade: true,
            light_emission: 0,
        })])),
        gui_light: None,
        ambient_occlusion: None,
        transforms: Some(Transforms { slots: [TransformSlot::None; 9], values: Vec::new() }),
        textures: vec![("particle".into(), Slot::Reference("missingno".into())), ("missingno".into(), Slot::Value(Location::new("minecraft", "missingno")))],
        parent: None,
    }
}

/// `ItemModelGenerator`: `particle` is `#layer0`, front-lit, generated.
pub(crate) fn generated() -> Model {
    Model {
        geometry: Some(Geometry::Generated),
        gui_light: Some(GuiLight::Front),
        textures: vec![("particle".into(), Slot::Reference("layer0".into()))],
        ..Model::default()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_models_like_gson() {
        let model = decode(
            br##"{"parent": "block/cube", "ambientocclusion": "TRUE", "gui_light": "front",
                "textures": {"all": "#side", "side": "block/stone", "one": ["block/dirt"]},
                "display": {"thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 200], "scale": [0.375, 9, -9]}, "gui": null},
                "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "rotation": {"origin": [8, 8, 8], "axis": "Y", "angle": 22.5, "rescale": true},
                    "faces": {"up": {"texture": "#all", "cullface": "nope", "uv": [0, 0, 16, 16], "rotation": -90}, "down": null}}, null]}"##,
        )
        .unwrap();
        assert_eq!(Some(Location::new("minecraft", "block/cube")), model.parent);
        assert_eq!(Some(true), model.ambient_occlusion);
        assert_eq!(Some(GuiLight::Front), model.gui_light);
        assert_eq!(Slot::Value(Location::new("minecraft", "block/dirt")), model.textures[2].1, "a one-element array reads as its element");
        let transforms = model.transforms.unwrap();
        assert_eq!(TransformSlot::Value(0), transforms.slots[0], "the left hand shares the right hand's object");
        assert_eq!(TransformSlot::Null, transforms.slots[5]);
        assert_eq!([0.0, 0.15625, 5.0], transforms.values[0].translation);
        assert_eq!([0.375, 4.0, -4.0], transforms.values[0].scale);
        let Some(Geometry::Elements(elements)) = model.geometry else { panic!() };
        assert!(elements[1].is_none());
        let element = elements[0].as_ref().unwrap();
        assert_eq!([0.5, 0.5, 0.5], element.rotation.unwrap().origin);
        let up = element.faces[Direction::Up.index()].clone().unwrap().unwrap();
        assert_eq!((None, Quadrant(3)), (up.cull, up.rotation));
        assert_eq!(Some(None), element.faces[Direction::Down.index()]);
        assert!(decode(br##"{"elements": [{"from": [0,0,0], "to": [16,16,99], "faces": {"up": {"texture": "#a"}}}]}"##).is_err());
        assert!(decode(br#"{"elements": [{"from": [0,0,0], "to": [1,1,1], "faces": {}}]}"#).is_err());
        assert!(decode(br##"{"elements": [{"from": [0,0,0], "to": [1,1,1], "shade": 1, "faces": {"up": {"texture": "#a"}}}]}"##).is_err());
        assert!(decode(br#"{"gui_light": "back"}"#).is_err());
        assert!(decode(br#"{"textures": {"a": "Bad Name"}}"#).is_err());
        assert!(decode(b"null").is_err());
        assert!(decode(b"{} trailing").is_ok(), "GsonHelper.fromJson reads one value");
    }
}
