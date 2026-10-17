//! Authored item-layer transforms. Immutable CPU owners are consumed directly
//! by native GUI decoding; compatibility consumers may read scoped CPU views.
mod ffi;
#[cfg(test)]
mod tests;
pub(crate) mod world_pose;

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub(crate) struct Pose {
    pub model: [f32; 16],
    pub normal: [f32; 9],
    pub trusted_normals: u32,
}
#[repr(C)]
pub(crate) struct Owner {
    pub poses: [Pose; 2],
    authored: [world_pose::AuthoredTransform; 2],
    pub safe_for_world: u32,
}
const _: () = assert!(
    std::mem::size_of::<Owner>() == 340 && std::mem::offset_of!(Owner, safe_for_world) == 336
);
impl Owner {
    pub(crate) fn new(authored: [f32; 9], no_transform: bool) -> Option<Self> {
        let authored = [
            world_pose::AuthoredTransform::new(authored, false, no_transform),
            world_pose::AuthoredTransform::new(authored, true, no_transform),
        ];
        let identity = world_pose::ResolvedPose {
            model: [
                1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.,
            ],
            normal: [1., 0., 0., 0., 1., 0., 0., 0., 1.],
            trusted: true,
        };
        let poses = authored.map(|transform| {
            let pose = transform
                .apply(identity, 30)
                .expect("identity parent is affine");
            Pose {
                model: pose.model,
                normal: pose.normal,
                trusted_normals: pose.trusted as u32,
            }
        });
        poses
            .iter()
            .all(|p| p.model.iter().chain(&p.normal).all(|v| v.is_finite()))
            .then_some(Self {
                safe_for_world: poses
                    .iter()
                    .all(|p| p.model.iter().chain(&p.normal).all(|v| v.abs() <= 1.0e10))
                    as u32,
                poses,
                authored,
            })
    }
}
impl Owner {
    pub(crate) fn resolve(
        &self,
        mode: u32,
        parent: world_pose::ResolvedPose,
        properties: u32,
    ) -> Option<Pose> {
        let hand = match mode {
            1 | 3 => 0,
            2 | 4 => 1,
            _ => return None,
        };
        let local = self.poses[hand];
        let p = if mode <= 2 {
            self.authored[hand].apply(parent, properties)
        } else {
            world_pose::compose(
                parent,
                properties,
                world_pose::ResolvedPose {
                    model: local.model,
                    normal: local.normal,
                    trusted: local.trusted_normals != 0,
                },
            )
        }?;
        p.model
            .iter()
            .chain(&p.normal)
            .all(|v| v.is_finite())
            .then_some(Pose {
                model: p.model,
                normal: p.normal,
                trusted_normals: p.trusted as u32,
            })
    }
}
