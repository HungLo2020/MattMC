use std::ffi::{CStr, CString};
use std::sync::Arc;

use ash::vk;

use crate::render::vulkanic::error::{GalError, GalResult};

#[allow(dead_code)]
pub(super) struct VulkanContext {
    #[allow(dead_code)]
    pub(super) entry: ash::Entry,
    pub(super) instance: ash::Instance,
    pub(super) physical_device: vk::PhysicalDevice,
    pub(super) device: ash::Device,
    pub(super) debug_utils: Option<ash::ext::debug_utils::Device>,
    pub(super) queue_family_index: u32,
    pub(super) queue: vk::Queue,
    pub(super) memory_properties: vk::PhysicalDeviceMemoryProperties,
    pub(super) command_pool: vk::CommandPool,
    pub(super) timeline: vk::Semaphore,
}

impl VulkanContext {
    pub(super) fn new(label: &str, validation: ValidationMode) -> GalResult<Arc<Self>> {
        let entry = unsafe { ash::Entry::load() }
            .map_err(|error| GalError::backend(format!("failed to load Vulkan entry: {error}")))?;
        let app_name = CString::new(label)
            .map_err(|_| GalError::backend("Vulkan application label contains NUL"))?;
        let engine_name =
            CString::new("MattMC VulkanicGAL").expect("static Vulkan engine name is valid");

        let available_layers = unsafe { entry.enumerate_instance_layer_properties() }
            .map_err(|error| GalError::backend(format!("failed to enumerate layers: {error:?}")))?;
        let available_extensions = unsafe { entry.enumerate_instance_extension_properties(None) }
            .map_err(|error| {
            GalError::backend(format!("failed to enumerate extensions: {error:?}"))
        })?;
        let enable_validation = validation != ValidationMode::Off
            && has_layer(&available_layers, "VK_LAYER_KHRONOS_validation");
        let validation_layer =
            CString::new("VK_LAYER_KHRONOS_validation").expect("static layer name is valid");
        let layer_names = if enable_validation {
            vec![validation_layer.as_ptr()]
        } else {
            Vec::new()
        };

        let app_info = vk::ApplicationInfo::default()
            .application_name(&app_name)
            .application_version(1)
            .engine_name(&engine_name)
            .engine_version(1)
            .api_version(vk::make_api_version(0, 1, 3, 0));
        let extension_names = if has_extension(&available_extensions, ash::ext::debug_utils::NAME) {
            vec![ash::ext::debug_utils::NAME.as_ptr()]
        } else {
            Vec::new()
        };
        let validation_feature_enables = match validation {
            ValidationMode::Off => Vec::new(),
            ValidationMode::Routine => vec![
                vk::ValidationFeatureEnableEXT::SYNCHRONIZATION_VALIDATION,
                vk::ValidationFeatureEnableEXT::BEST_PRACTICES,
            ],
            ValidationMode::Deep => vec![
                vk::ValidationFeatureEnableEXT::GPU_ASSISTED,
                vk::ValidationFeatureEnableEXT::GPU_ASSISTED_RESERVE_BINDING_SLOT,
                vk::ValidationFeatureEnableEXT::SYNCHRONIZATION_VALIDATION,
                vk::ValidationFeatureEnableEXT::BEST_PRACTICES,
            ],
        };
        let mut validation_features = vk::ValidationFeaturesEXT::default()
            .enabled_validation_features(&validation_feature_enables);
        let mut instance_info = vk::InstanceCreateInfo::default()
            .application_info(&app_info)
            .enabled_layer_names(&layer_names)
            .enabled_extension_names(&extension_names);
        if enable_validation && !validation_feature_enables.is_empty() {
            instance_info = instance_info.push_next(&mut validation_features);
        }
        let instance = unsafe { entry.create_instance(&instance_info, None) }.map_err(|error| {
            GalError::backend(format!("failed to create Vulkan instance: {error:?}"))
        })?;

        let physical_devices =
            unsafe { instance.enumerate_physical_devices() }.map_err(|error| {
                GalError::backend(format!(
                    "failed to enumerate Vulkan physical devices: {error:?}"
                ))
            })?;
        let (physical_device, queue_family_index) =
            select_graphics_device(&instance, &physical_devices)?;
        let memory_properties =
            unsafe { instance.get_physical_device_memory_properties(physical_device) };

        let priority = [1.0_f32];
        let queue_info = vk::DeviceQueueCreateInfo::default()
            .queue_family_index(queue_family_index)
            .queue_priorities(&priority);
        let mut dynamic_rendering =
            vk::PhysicalDeviceDynamicRenderingFeatures::default().dynamic_rendering(true);
        let mut synchronization2 =
            vk::PhysicalDeviceSynchronization2Features::default().synchronization2(true);
        let mut timeline_features =
            vk::PhysicalDeviceTimelineSemaphoreFeatures::default().timeline_semaphore(true);
        let queue_infos = [queue_info];
        let device_info = vk::DeviceCreateInfo::default()
            .queue_create_infos(&queue_infos)
            .push_next(&mut dynamic_rendering)
            .push_next(&mut synchronization2)
            .push_next(&mut timeline_features);
        let device = unsafe { instance.create_device(physical_device, &device_info, None) }
            .map_err(|error| {
                GalError::backend(format!("failed to create Vulkan device: {error:?}"))
            })?;
        let debug_utils = if extension_names.is_empty() {
            None
        } else {
            Some(ash::ext::debug_utils::Device::new(&instance, &device))
        };
        let queue = unsafe { device.get_device_queue(queue_family_index, 0) };
        let command_pool_info = vk::CommandPoolCreateInfo::default()
            .queue_family_index(queue_family_index)
            .flags(vk::CommandPoolCreateFlags::RESET_COMMAND_BUFFER);
        let command_pool = unsafe { device.create_command_pool(&command_pool_info, None) }
            .map_err(|error| {
                GalError::backend(format!("failed to create command pool: {error:?}"))
            })?;
        let mut timeline_info = vk::SemaphoreTypeCreateInfo::default()
            .semaphore_type(vk::SemaphoreType::TIMELINE)
            .initial_value(0);
        let semaphore_info = vk::SemaphoreCreateInfo::default().push_next(&mut timeline_info);
        let timeline =
            unsafe { device.create_semaphore(&semaphore_info, None) }.map_err(|error| {
                GalError::backend(format!("failed to create timeline semaphore: {error:?}"))
            })?;

        let context = Arc::new(Self {
            entry,
            instance,
            physical_device,
            device,
            debug_utils,
            queue_family_index,
            queue,
            memory_properties,
            command_pool,
            timeline,
        });
        context.set_object_name(context.timeline, "gal.timeline.graphics");
        Ok(context)
    }

    pub(super) fn allocate_memory(
        &self,
        requirements: vk::MemoryRequirements,
        properties: vk::MemoryPropertyFlags,
    ) -> GalResult<vk::DeviceMemory> {
        let memory_type_index = self.find_memory_type(requirements.memory_type_bits, properties)?;
        let alloc_info = vk::MemoryAllocateInfo::default()
            .allocation_size(requirements.size)
            .memory_type_index(memory_type_index);
        unsafe { self.device.allocate_memory(&alloc_info, None) }.map_err(|error| {
            GalError::backend(format!("failed to allocate Vulkan memory: {error:?}"))
        })
    }

    pub(super) fn find_memory_type(
        &self,
        type_bits: u32,
        required: vk::MemoryPropertyFlags,
    ) -> GalResult<u32> {
        for index in 0..self.memory_properties.memory_type_count {
            let mask = 1_u32 << index;
            let properties = self.memory_properties.memory_types[index as usize].property_flags;
            if type_bits & mask != 0 && properties.contains(required) {
                return Ok(index);
            }
        }
        Err(GalError::backend(format!(
            "no Vulkan memory type matches bits=0x{type_bits:x} required=0x{:x}",
            required.as_raw()
        )))
    }

    pub(super) fn wait_idle(&self) -> GalResult<()> {
        unsafe { self.device.device_wait_idle() }.map_err(|error| {
            GalError::backend(format!("Vulkan device wait idle failed: {error:?}"))
        })
    }

    pub(super) fn write_mapped_memory(
        &self,
        memory: vk::DeviceMemory,
        offset: u64,
        bytes: &[u8],
    ) -> GalResult<()> {
        let ptr = unsafe {
            self.device.map_memory(
                memory,
                offset,
                bytes.len() as u64,
                vk::MemoryMapFlags::empty(),
            )
        }
        .map_err(|error| {
            GalError::backend(format!("failed to map Vulkan upload memory: {error:?}"))
        })?;
        unsafe {
            std::ptr::copy_nonoverlapping(bytes.as_ptr(), ptr.cast::<u8>(), bytes.len());
            self.device.unmap_memory(memory);
        }
        Ok(())
    }

    pub(super) fn read_mapped_memory(
        &self,
        memory: vk::DeviceMemory,
        offset: u64,
        size: u64,
    ) -> GalResult<Vec<u8>> {
        let size = usize::try_from(size)
            .map_err(|_| GalError::backend("Vulkan readback size does not fit usize"))?;
        let ptr = unsafe {
            self.device
                .map_memory(memory, offset, size as u64, vk::MemoryMapFlags::empty())
        }
        .map_err(|error| {
            GalError::backend(format!("failed to map Vulkan readback memory: {error:?}"))
        })?;
        let bytes = unsafe { std::slice::from_raw_parts(ptr.cast::<u8>(), size).to_vec() };
        unsafe { self.device.unmap_memory(memory) };
        Ok(bytes)
    }

    pub(super) fn set_object_name<T: vk::Handle>(&self, object: T, name: &str) {
        let Some(debug_utils) = &self.debug_utils else {
            return;
        };
        let Ok(name) = CString::new(name) else {
            return;
        };
        let info = vk::DebugUtilsObjectNameInfoEXT::default()
            .object_handle(object)
            .object_name(&name);
        let _ = unsafe { debug_utils.set_debug_utils_object_name(&info) };
    }

    pub(super) unsafe fn begin_label(&self, command_buffer: vk::CommandBuffer, name: &str) {
        let Some(debug_utils) = &self.debug_utils else {
            return;
        };
        let Ok(name) = CString::new(name) else {
            return;
        };
        let label = vk::DebugUtilsLabelEXT::default()
            .label_name(&name)
            .color([0.15, 0.45, 0.95, 1.0]);
        unsafe { debug_utils.cmd_begin_debug_utils_label(command_buffer, &label) };
    }

    pub(super) unsafe fn end_label(&self, command_buffer: vk::CommandBuffer) {
        if let Some(debug_utils) = &self.debug_utils {
            unsafe { debug_utils.cmd_end_debug_utils_label(command_buffer) };
        }
    }
}

impl Drop for VulkanContext {
    fn drop(&mut self) {
        unsafe {
            let _ = self.device.device_wait_idle();
            self.device.destroy_semaphore(self.timeline, None);
            self.device.destroy_command_pool(self.command_pool, None);
            self.device.destroy_device(None);
            self.instance.destroy_instance(None);
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) enum ValidationMode {
    Off,
    Routine,
    Deep,
}

impl ValidationMode {
    pub(super) fn from_env() -> Self {
        match std::env::var("MATTMC_VULKAN_VALIDATE") {
            Ok(value) if value.eq_ignore_ascii_case("deep") => Self::Deep,
            Ok(value)
                if value.eq_ignore_ascii_case("1")
                    || value.eq_ignore_ascii_case("true")
                    || value.eq_ignore_ascii_case("routine") =>
            {
                Self::Routine
            }
            _ => Self::Off,
        }
    }
}

fn has_layer(layers: &[vk::LayerProperties], wanted: &str) -> bool {
    layers.iter().any(|layer| {
        let name = unsafe { CStr::from_ptr(layer.layer_name.as_ptr()) };
        name.to_string_lossy() == wanted
    })
}

fn has_extension(extensions: &[vk::ExtensionProperties], wanted: &CStr) -> bool {
    extensions.iter().any(|extension| {
        let name = unsafe { CStr::from_ptr(extension.extension_name.as_ptr()) };
        name == wanted
    })
}

fn select_graphics_device(
    instance: &ash::Instance,
    physical_devices: &[vk::PhysicalDevice],
) -> GalResult<(vk::PhysicalDevice, u32)> {
    let mut best = None;
    for physical_device in physical_devices {
        let properties = unsafe { instance.get_physical_device_properties(*physical_device) };
        let queue_families =
            unsafe { instance.get_physical_device_queue_family_properties(*physical_device) };
        let Some((queue_family_index, _)) = queue_families
            .iter()
            .enumerate()
            .find(|(_, family)| family.queue_flags.contains(vk::QueueFlags::GRAPHICS))
        else {
            continue;
        };
        let score = match properties.device_type {
            vk::PhysicalDeviceType::DISCRETE_GPU => 3,
            vk::PhysicalDeviceType::INTEGRATED_GPU => 2,
            vk::PhysicalDeviceType::CPU => 1,
            _ => 0,
        };
        if best
            .map(|(_, _, best_score)| score > best_score)
            .unwrap_or(true)
        {
            best = Some((*physical_device, queue_family_index as u32, score));
        }
    }
    best.map(|(device, queue, _)| (device, queue))
        .ok_or_else(|| GalError::backend("no Vulkan physical device exposes a graphics queue"))
}
