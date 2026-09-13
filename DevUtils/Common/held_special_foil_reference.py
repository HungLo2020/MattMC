"""Held SPECIAL foil evidence. This private diagnostic never admits a route.

The stopped oracles are Frozen OpenGL r632(clock), r635(compass) and r637(recovery compass),
inspected without fitting the candidate. The GUI companion retains its own full source/timing/icon checks.
Moving clock/compass/recovery-compass phases use aligned actual Frozen pairs at the same fixed probes.
Other items/phases require their own oracle and fail closed here.
"""
import math
import re
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

PACK_SHA256 = "212b54dc0cc5d4ba3749dfa1b0ad16b2316edfec72c00b6e1fbb2e052d8182e1"
# XXH32(seed0) of independently generated gui_foil_reference.pattern_pixel RGBA.
PATTERN_RGBA_XXH32 = "23e4c5c8"
CLOCK_PROBES = (
    (1040,390,(160,96,29)), (1100,370,(160,97,30)),
    (1180,380,(161,98,30)), (1240,415,(161,97,30)),
    (1060,445,(71,84,142)), (1220,470,(213,181,71)),
    (1100,520,(216,211,16)), (1240,580,(83,101,176)),
    (1090,625,(46,41,32)), (1270,650,(45,39,32)),
    (1150,600,(72,85,142)), (1210,650,(45,39,32)),
)


COMPASS_PROBES = (
    (1020,440,(67,61,55)), (1120,460,(125,120,113)),
    (1180,432,(69,63,56)), (1100,500,(64,59,51)),
    (1200,520,(64,59,52)), (1050,550,(63,59,51)),
    (1120,580,(215,39,31)), (1080,594,(214,39,31)),
    (1230,588,(64,60,52)), (1100,704,(209,206,200)),
    (1260,703,(59,54,49)), (1150,635,(86,81,72)),
)
RECOVERY_PROBES = (
    (1020,440,(152,146,140)), (1120,460,(41,85,81)),
    (1180,432,(100,94,87)), (1100,500,(37,40,30)),
    (1200,520,(38,40,31)), (1050,550,(36,40,30)),
    (1120,580,(29,85,105)), (1080,594,(36,40,30)),
    (1230,588,(38,41,31)), (1100,704,(145,142,136)),
    (1260,703,(94,89,84)), (1150,635,(37,41,30)),
)
HELD_SCOPES = {
    "held-clock": dict(slot=2, vertices=88, indices=132, probes=CLOCK_PROBES),
    "held-compass": dict(slot=3, vertices=72, indices=108, probes=COMPASS_PROBES),
    "held-recovery-compass": dict(slot=3, vertices=88, indices=132, probes=RECOVERY_PROBES),
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def integer(value, minimum=0):
    return type(value) is int and minimum <= value <= 2**64-1


def vector(value, size):
    return (isinstance(value, list) and len(value) == size
            and all(type(v) in (int, float) and math.isfinite(v) for v in value))


def nonsingular3(values):
    a,b,c,d,e,f,g,h,i=values
    determinant=a*(e*i-f*h)-d*(b*i-c*h)+g*(b*f-c*e)
    return math.isfinite(determinant) and determinant != 0


def observed_hand(receipt, phase=None):
    require(phase is None or type(phase) is int and phase in (10000,40000),
            "unsupported held special foil phase")
    require(isinstance(receipt,dict), "missing held timing document")
    hand = receipt.get("hand", {})
    require(isinstance(hand,dict), "missing held timing record")
    ticks = hand.get("scaledTicks")
    require(hand.get("enabled") is True and hand.get("complete") is True
            and integer(hand.get("frameSequence"), 1)
            and isinstance(ticks,list) and len(ticks) == 1
            and type(ticks[0]) is int and 0 <= ticks[0] <= 2**63-1,
            "held special foil requires a complete observed hand clock")
    require(ticks[0] == 0 if phase is None else ticks[0] > 0 and (ticks[0]-phase)%330000 <= 512,
            "held special foil missed the requested natural phase")
    require(receipt.get("enabled") is True and receipt.get("complete") is True
            and integer(receipt.get("frameSequence"),1)
            and receipt["frameSequence"] == hand["frameSequence"],
            "held and GUI timing must observe the same captured frame")
    return hand


def stopped_hand(receipt):
    return observed_hand(receipt)


def native_inputs(receipt, owner, ack, timing, *, context="held-clock", phase=None):
    require(context in HELD_SCOPES, "unknown held special foil scope")
    validate_native_frame(receipt,owner,ack)
    hand=observed_hand(timing,phase)
    require(hand["frameSequence"] == receipt["gameplay_frame_id"], "stale held timing receipt")
    semantics,draws=receipt.get("semantic_instances"),receipt.get("draws")
    require(isinstance(semantics,list) and len(semantics)==1 and isinstance(draws,list) and len(draws)==1,
            "held special item requires exactly one semantic decal and one actual draw")
    require(isinstance(semantics[0],dict) and isinstance(draws[0],dict), "malformed held decal records")
    result=validate_decal_draw(semantics[0],draws[0],HELD_SCOPES[context],first_person=True,
                               expected_ticks=hand["scaledTicks"][0],phase=phase)
    return dict(result,submission=receipt["gal_submission_id"],correlation=receipt["correlation_id"])


def validate_native_frame(receipt,owner,ack):
    require(all(isinstance(v,dict) for v in (receipt,owner,ack)), "missing held native documents")
    require(receipt.get("schema") == "world-decal-submission-inputs-v1"
            and receipt.get("complete") is True and receipt.get("gpu_readback") is False,
            "missing complete native decal submission-input receipt")
    correspondence = {"gameplay_frame_id":"gameplayFrameId", "correlation_id":"correlationId",
                      "gal_submission_id":"submissionId"}
    presentation = ack.get("wholeFramePresentationCorrelation", {})
    require(isinstance(presentation,dict), "missing selected held presentation")
    for native, java in correspondence.items():
        require(integer(receipt.get(native), 1) and type(owner.get(native)) is int
                and type(presentation.get(java)) is int
                and receipt[native] == owner[native] == presentation[java],
                "held decal receipt is not from the selected presentation")
    require(integer(receipt.get("deterministic_rendered_frame_index"), 1)
            and type(owner.get("deterministic_rendered_frame_index")) is int
            and type(ack.get("renderedFrameIndex")) is int
            and receipt["deterministic_rendered_frame_index"] == owner["deterministic_rendered_frame_index"]
            == ack["renderedFrameIndex"], "held decal deterministic frame mismatch")
    require(owner.get("artifact_class") == "rust_vulkan_whole_frame_gameplay_correlation"
            and owner.get("rust_whole_frame_presenter") is True
            and owner.get("java_vulkan_frame_execution") is False
            and owner.get("same_acquired_presented_image") is True
            and type(owner.get("world_lod_instances")) is int and owner["world_lod_instances"] == 0
            and owner.get("world_lod_route_selected") is False,
            "held decal requires exclusively Rust-owned vanilla presentation")
    for key, ack_key in (("acquired_swapchain_image", "acquiredSwapchainImage"),
                         ("presented_swapchain_image", "presentedSwapchainImage")):
        require(integer(owner.get(key), 1) and type(presentation.get(ack_key)) is int
                and owner[key] == presentation[ack_key], "held decal swapchain image mismatch")
    require(owner["acquired_swapchain_image"] == owner["presented_swapchain_image"]
            and type(owner.get("present_completed_submission_id")) is int
            and owner["present_completed_submission_id"] == receipt["gal_submission_id"],
            "held decal presentation completion mismatch")


def validate_decal_draw(semantic,draw,scope,*,first_person,expected_ticks,phase=None):
    require(semantic.get("context") == ("first-person" if first_person else "world") and semantic.get("first_person") is first_person
            and semantic.get("trusted_normals") is True
            and vector(semantic.get("model_pose"), 16) and vector(semantic.get("normal_pose"), 9),
            "held special item requires copied first-person model and normal poses")
    model=semantic["model_pose"]
    require([model[i] for i in (3,7,11,15)] == [0,0,0,1]
            and nonsingular3([model[i] for i in (0,1,2,4,5,6,8,9,10)])
            and nonsingular3(semantic["normal_pose"]), "held special item requires invertible affine poses")
    require(integer(semantic.get("clock_millis")) and semantic["clock_millis"] <= 2**63-1
            and type(semantic.get("speed")) in (int,float) and semantic["speed"] == (0 if phase is None else 0.5)
            and type(semantic.get("strength")) in (int,float) and semantic["strength"] == 0.5
            and type(semantic.get("scaled_ticks")) is int
            and semantic["scaled_ticks"] == expected_ticks
            and semantic["scaled_ticks"] == min(2**63-1,int(float(semantic["clock_millis"])*semantic["speed"]*8)),
            "held special item native material timing/settings mismatch")
    require(draw.get("program") == "vulkanic:builtin/direct_world_decal_foil_v1"
            and type(draw.get("material_mode")) is int and draw["material_mode"] == 4,
            "held special item did not use the native decal material")
    for key in ("mesh_key", "mesh_generation"):
        require(integer(semantic.get(key), 1) and type(draw.get(key)) is int
                and semantic[key] == draw[key], "held special item mesh incarnation mismatch")
    for key, expected in (("instance_count",1),("vertex_count",scope["vertices"]),("index_count",scope["indices"]),
                          ("payload_bytes",48+8*scope["vertices"]),("texture_width",16),("texture_height",16)):
        require(type(draw.get(key)) is int and draw[key] == expected,
                "held special item geometry/material extent mismatch")
    require(draw.get("texture_rgba_xxh32") == PATTERN_RGBA_XXH32,
            "held special item did not bind the authored asymmetric glint texture")
    require(draw.get("sampler") == dict(min_filter="Linear",mag_filter="Linear",mip_filter="Nearest",
            address_u="Repeat",address_v="Repeat",comparison=None),
            "held special item glint sampler differs from the reference")
    uploaded = draw.get("uploaded_instances")
    require(isinstance(uploaded, list) and len(uploaded) == 1, "missing held special item upload")
    uploaded = uploaded[0]
    require(isinstance(uploaded,dict), "malformed held decal upload")
    require(vector(uploaded.get("model_pose"),16) and uploaded["model_pose"] == semantic["model_pose"]
            and type(uploaded.get("strength")) in (int,float) and uploaded["strength"] == 0.5,
            "held special item upload differs from copied semantics")
    # Frozen setupGlintTexturing: independently reduced translation, then
    # Rz(PI/18) * scale8, using the captured draw's actual clock.
    ticks = semantic["scaled_ticks"]
    rows = [8*math.cos(math.pi/18),-8*math.sin(math.pi/18),-(ticks%110000)/110000,0,
            8*math.sin(math.pi/18),8*math.cos(math.pi/18),(ticks%30000)/30000,0]
    require(vector(uploaded.get("foil_rows"),8)
            and all(abs(a-b) <= 2e-6 for a,b in zip(uploaded["foil_rows"],rows)),
            "held special item uploaded foil transform differs from Frozen")
    # Generated mesh face order is not stable across processes. Use the actual
    # source positions/normals for the sampled payload vertices, never accept
    # a candidate-chosen UV alternative. Frozen SheetedDecal's six cardinal
    # projections are independently covered by the Java reference test.
    vertices=draw.get("first_source_vertices")
    require(isinstance(vertices,list) and len(vertices)==4, "missing sampled decal source geometry")
    uv=[]
    for vertex in vertices:
        require(isinstance(vertex,dict) and vector(vertex.get("position"),3)
                and integer(vertex.get("normal_packed")), "malformed sampled decal source vertex")
        x,y,z=vertex["position"];normal=vertex["normal_packed"]
        require(all(0<=v<=1 for v in (x,y,z)), "held special item source vertex outside generated item")
        projections={0x00008100:(x,-z),0x00007f00:(x,z),0x00810000:(-x,-y),
                     0x007f0000:(x,-y),0x00000081:(-z,-y),0x0000007f:(z,-y)}
        require(normal in projections, "held special item requires a cardinal generated source normal")
        uv.extend(v/(96 if first_person else 128) for v in projections[normal])
    require(len({v["normal_packed"] for v in vertices}) == 1,
            "held special item sampled quad must retain one generated face normal")
    require(type(uploaded.get("uv_word_offset")) is int and uploaded["uv_word_offset"] == 12
            and vector(uploaded.get("first_projected_uvs"),8)
            and all(abs(a-b) <= 2e-6 for a,b in zip(uploaded["first_projected_uvs"],uv))
            and isinstance(uploaded.get("projected_uv_xxh32"),str)
            and re.fullmatch("[0-9a-f]{8}",uploaded["projected_uv_xxh32"]),
            "held special item lacks native first-person projected UVs")
    return dict(passed=True,material=draw["program"],projected_uv_hash=uploaded["projected_uv_xxh32"])


def clock_pixels(frozen, current):
    return held_pixels(frozen,current,context="held-clock")


def held_pixels(frozen, current, *, context, phase=None):
    require(context in HELD_SCOPES, "unknown held special foil pixel scope")
    require(phase is None or context in ("held-clock", "held-compass", "held-recovery-compass") and type(phase) is int and phase in (10000,40000),
            "unsupported moving held special foil pixel scope")
    require(frozen.size == current.size == (1280,720), "held special item requires the fixed1280x720 viewport")
    images = [im.convert("RGB") for im in (frozen,current)]
    probes=[]
    for x,y,expected in HELD_SCOPES[context]["probes"]:
        values=[im.getpixel((x,y)) for im in images]
        error=(max(abs(a-b) for value in values for a,b in zip(value,expected)) if phase is None
               else max(abs(a-b) for a,b in zip(*values)))
        # Presence bound only: a strength .5 SRC_COLOR/ONE glint layer can
        # contribute at most .5**2 * 255 ~= 64 over the unchanged item base.
        # Animated color parity still requires <=1 against actual Frozen pixels.
        visible=phase is None or max(abs(a-b) for value in values for a,b in zip(value,expected)) <= 64
        probes.append(dict(position=[x,y],frozen=values[0],current=values[1],
                           expected=expected if phase is None else None, base_material_visible=visible,
                           max_channel_error=error,passed=visible and error<=1))
    # Fixed whole held region; no segmentation/alignment fitted to Current.
    box=(960,336,1280,720)
    means=ImageStat.Stat(ImageChops.difference(*[im.crop(box) for im in images])).mean
    return dict(passed=all(p["passed"] for p in probes) and max(means)<=1,
                probes=probes,box=box,mean_rgb_error=means,phase=phase)


def moving_clock_change(before_frozen, before_current, after_frozen, after_current):
    return moving_held_change(before_frozen,before_current,after_frozen,after_current,context="held-clock")


def moving_held_change(before_frozen, before_current, after_frozen, after_current, *, context):
    require(context in ("held-clock", "held-compass", "held-recovery-compass"), "unsupported temporal held special foil scope")
    before=held_pixels(before_frozen,before_current,context=context,phase=10000)
    after=held_pixels(after_frozen,after_current,context=context,phase=40000)
    require(before["passed"] and after["passed"], "temporal held foil requires two passing pixel pairs")
    rows=[]
    for old,new in zip(before["probes"],after["probes"]):
        deltas=[[new[key][c]-old[key][c] for c in range(3)] for key in ("frozen","current")]
        error=max(abs(a-b) for a,b in zip(*deltas))
        changed=any(abs(a)>4 and abs(b)>4 and a*b>0 for a,b in zip(*deltas))
        rows.append(dict(position=old["position"],deltas=deltas,delta_error=error,changed=changed,passed=error<=2))
    groups=({"rim":(0,1,2,3),"blue_face":(4,7,10),"yellow_face":(5,6),"dark_face":(8,9,11)}
            if context == "held-clock" else
            {"rim":(0,1,2,9,10),"dark_face":(3,4,5,8,11),"red_needle":(6,7)}
            if context == "held-compass" else
            {"rim":(0,2,9,10),"dark_face":(3,4,5,7,8,11),"cyan_needle":(1,6)})
    changes={name:any(rows[i]["changed"] for i in indexes) for name,indexes in groups.items()}
    return dict(passed=all(changes.values()) and all(row["passed"] for row in rows),groups_changed=changes,probes=rows)


def check_pair(pair, phase=None, *, context="held-clock"):
    require(context in HELD_SCOPES, "unknown held special foil scope")
    import graphics_harness as h
    require(phase is None or context in ("held-clock", "held-compass", "held-recovery-compass") and type(phase) is int and phase in (10000,40000),
            "unsupported moving held special foil scope")
    documents=[]
    hand_ticks=[]
    for key, image_key, mode in (("baseline_artifact","baseline_image","frozen-opengl-shaders-off"),
                                  ("current_artifact","current_image","current-rust-vulkan-shaders-off")):
        path=Path(pair[key]); artifact=h.read_json(path); doc=h.deterministic_capture_document(path) or {}
        require(artifact.get("mode",{}).get("name") == mode
                and artifact.get("capture",{}).get("exit_code") == 0
                and type(artifact["capture"]["exit_code"]) is int,
                "held special foil requires successful actual Rust Vulkan/Frozen OpenGL clients")
        health=artifact.get("validation",{})
        require(all(health.get(k) is True for k in ("complete","crash_free","device_loss_free"))
                and all(health.get(k) is False for k in ("rss_guard_triggered","orphan_process_detected")),
                "held special foil capture is unhealthy")
        if key == "current_artifact":
            require(health.get("vulkan_validation_clean") is True, "held special foil Vulkan validation failed")
        meta_path=h.latest_capture_meta_path(path.parent/"capture")
        require(meta_path is not None, "missing held special foil metadata")
        meta=h.read_key_values(meta_path)
        require(meta.get("forced_option_guiScale") == "3"
                and meta.get("gui_resource_pack_scenario") == ("special-item-foil-pattern" if phase is None else "special-item-foil-moving")
                and meta.get("gui_resource_pack_mattmc-special-item-foil-pattern_sha256") == PACK_SHA256,
                "held special item requires the exact authored patterned pack and GUI scale3")
        reload=doc.get("worldResourceReload",{})
        require(reload.get("requested") is True and reload.get("complete") is True
                and reload.get("selectedAtCapture") == ["vanilla","file/mattmc-special-item-foil-pattern"],
                "held special item requires a completed normal resource reload")
        captures=doc.get("captures")
        require(isinstance(captures,list) and len(captures)==1, "held special item requires one selected capture")
        capture=captures[0]
        require(Path(capture.get("screenshot","")) == Path(pair[image_key])
                and capture.get("poseName") == "initial" and capture.get("gameTime") == 6000
                and capture.get("position") == dict(x=150.5,y=100.0,z=530.5)
                and capture.get("observedYaw") == 105 and capture.get("observedPitch") == 10
                and doc.get("cameraType") == "FIRST_PERSON"
                and doc.get("selectedHotbarSlot") == HELD_SCOPES[context]["slot"],
                "held special item capture/item/camera differs from the Frozen oracle")
        hand_ticks.append(observed_hand(h.read_json(Path(pair[image_key]+".foil-timing.json")),phase)["scaledTicks"][0])
        documents.append(doc)
    distance=min((hand_ticks[0]-hand_ticks[1])%330000,(hand_ticks[1]-hand_ticks[0])%330000)
    require(phase is None or distance<=16,"moving held special foil captures are not phase-aligned")
    current=Path(pair["current_image"])
    receipt=h.read_json(Path(str(current)+".decal-inputs.json"))
    require(integer(receipt.get("gameplay_frame_id"),1), "invalid native decal frame")
    owner=h.read_json(Path(pair["current_artifact"]).parent/"capture"/"whole_frame_gameplay_attachments"
                      /f"gameplay-correlation-frame-{receipt['gameplay_frame_id']}.json")
    ack=h.read_json(current.with_name("capture_request_01_initial.ack.json"))
    require(ack.get("status") == "captured" and ack.get("captureMethod") == "rust-vulkan-final-output"
            and Path(ack.get("screenshot","")) == current,
            "held special item requires the renderer-owned selected image")
    native=native_inputs(receipt,owner,ack,h.read_json(Path(str(current)+".foil-timing.json")),context=context,phase=phase)
    require(documents[1]["captures"][0]["renderedFrameIndex"] == receipt["deterministic_rendered_frame_index"],
            "held special item document and native frame differ")
    with Image.open(pair["baseline_image"]) as frozen, Image.open(current) as image:
        pixels=held_pixels(frozen,image,context=context,phase=phase)
    return dict(passed=pixels["passed"],capability_admitted=False,context=context,
                native=native,pixels=pixels,hand_ticks=hand_ticks,phase_distance=distance)
