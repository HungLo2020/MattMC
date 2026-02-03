mod client;

use std::sync::Arc;
use std::time::Instant;
use vulkano::buffer::{Buffer, BufferCreateInfo, BufferUsage};
use vulkano::command_buffer::allocator::{
    StandardCommandBufferAllocator, StandardCommandBufferAllocatorCreateInfo,
};
use vulkano::command_buffer::{
    AutoCommandBufferBuilder, CommandBufferUsage, RenderPassBeginInfo, SubpassBeginInfo,
    SubpassContents, SubpassEndInfo,
};
use vulkano::device::physical::PhysicalDeviceType;
use vulkano::device::{Device, DeviceCreateInfo, DeviceExtensions, QueueCreateInfo, QueueFlags};
use vulkano::image::view::ImageView;
use vulkano::image::Image;
use vulkano::instance::{Instance, InstanceCreateFlags, InstanceCreateInfo};
use vulkano::memory::allocator::{AllocationCreateInfo, MemoryTypeFilter, StandardMemoryAllocator};
use vulkano::pipeline::graphics::color_blend::{ColorBlendAttachmentState, ColorBlendState};
use vulkano::pipeline::graphics::input_assembly::InputAssemblyState;
use vulkano::pipeline::graphics::multisample::MultisampleState;
use vulkano::pipeline::graphics::rasterization::{RasterizationState, CullMode};
use vulkano::pipeline::graphics::vertex_input::{Vertex, VertexDefinition};
use vulkano::pipeline::graphics::viewport::{Viewport, ViewportState};
use vulkano::pipeline::graphics::GraphicsPipelineCreateInfo;
use vulkano::pipeline::layout::PipelineDescriptorSetLayoutCreateInfo;
use vulkano::pipeline::{
    DynamicState, GraphicsPipeline, PipelineLayout, PipelineShaderStageCreateInfo, Pipeline,
};
use vulkano::render_pass::{Framebuffer, FramebufferCreateInfo, RenderPass, Subpass};
use vulkano::swapchain::{
    acquire_next_image, Surface, Swapchain, SwapchainCreateInfo, SwapchainPresentInfo,
};
use vulkano::sync::{self, GpuFuture};
use vulkano::{Validated, VulkanError, VulkanLibrary};
use winit::event::{Event, WindowEvent, ElementState, VirtualKeyCode, DeviceEvent};
use winit::event_loop::{ControlFlow, EventLoop};
use winit::window::{Window, WindowBuilder};

use client::renderer::cube::CubeVertex;
use client::renderer::camera::Camera;
use client::renderer::shaders::{vertex_shader, fragment_shader};
use client::renderer::chunk::Chunk;

struct App {
    instance: Arc<Instance>,
    device: Arc<Device>,
    queue: Arc<vulkano::device::Queue>,
    surface: Arc<Surface>,
    swapchain: Arc<Swapchain>,
    images: Vec<Arc<Image>>,
    render_pass: Arc<RenderPass>,
    framebuffers: Vec<Arc<Framebuffer>>,
    pipeline: Arc<GraphicsPipeline>,
    vertex_buffer: vulkano::buffer::Subbuffer<[CubeVertex]>,
    viewport: Viewport,
    command_buffer_allocator: Arc<StandardCommandBufferAllocator>,
    memory_allocator: Arc<StandardMemoryAllocator>,
    recreate_swapchain: bool,
    previous_frame_end: Option<Box<dyn GpuFuture>>,
    camera: Camera,
    // Input state
    keys_pressed: std::collections::HashSet<VirtualKeyCode>,
    last_frame_time: Instant,
    cursor_locked: bool,
}

impl App {
    fn new(
        event_loop: &EventLoop<()>,
        instance: Arc<Instance>,
        device: Arc<Device>,
        queue: Arc<vulkano::device::Queue>,
    ) -> (Self, Arc<Window>) {
        let window = Arc::new(
            WindowBuilder::new()
                .with_title("MattMC Rust/Vulkan - Chunk Renderer")
                .with_inner_size(winit::dpi::LogicalSize::new(1280, 720))
                .build(event_loop)
                .unwrap(),
        );

        // Lock cursor for FPS-style controls
        window.set_cursor_visible(false);
        let _ = window.set_cursor_grab(winit::window::CursorGrabMode::Confined);

        let surface = Surface::from_window(instance.clone(), window.clone()).unwrap();

        let memory_allocator = Arc::new(StandardMemoryAllocator::new_default(device.clone()));
        let command_buffer_allocator = Arc::new(StandardCommandBufferAllocator::new(
            device.clone(),
            StandardCommandBufferAllocatorCreateInfo::default(),
        ));

        let (swapchain, images) = {
            let surface_capabilities = device
                .physical_device()
                .surface_capabilities(&surface, Default::default())
                .unwrap();
            let image_format = device
                .physical_device()
                .surface_formats(&surface, Default::default())
                .unwrap()[0]
                .0;

            Swapchain::new(
                device.clone(),
                surface.clone(),
                SwapchainCreateInfo {
                    min_image_count: surface_capabilities.min_image_count.max(2),
                    image_format,
                    image_extent: window.inner_size().into(),
                    image_usage: vulkano::image::ImageUsage::COLOR_ATTACHMENT,
                    composite_alpha: surface_capabilities
                        .supported_composite_alpha
                        .into_iter()
                        .next()
                        .unwrap(),
                    ..Default::default()
                },
            )
            .unwrap()
        };

        let render_pass = vulkano::single_pass_renderpass!(
            device.clone(),
            attachments: {
                color: {
                    format: swapchain.image_format(),
                    samples: 1,
                    load_op: Clear,
                    store_op: Store,
                },
            },
            pass: {
                color: [color],
                depth_stencil: {},
            },
        )
        .unwrap();

        // Create chunk and generate vertices
        println!("Generating chunk...");
        let chunk = Chunk::new();
        let vertices = chunk.generate_vertices();
        println!("Generated {} vertices for chunk", vertices.len());

        let vertex_buffer = Buffer::from_iter(
            memory_allocator.clone(),
            BufferCreateInfo {
                usage: BufferUsage::VERTEX_BUFFER,
                ..Default::default()
            },
            AllocationCreateInfo {
                memory_type_filter: MemoryTypeFilter::PREFER_DEVICE
                    | MemoryTypeFilter::HOST_SEQUENTIAL_WRITE,
                ..Default::default()
            },
            vertices,
        )
        .unwrap();

        let vs = vertex_shader::load(device.clone())
            .unwrap()
            .entry_point("main")
            .unwrap();
        let fs = fragment_shader::load(device.clone())
            .unwrap()
            .entry_point("main")
            .unwrap();

        let vertex_input_state = CubeVertex::per_vertex()
            .definition(&vs.info().input_interface)
            .unwrap();

        let stages = [
            PipelineShaderStageCreateInfo::new(vs),
            PipelineShaderStageCreateInfo::new(fs),
        ];

        let layout = PipelineLayout::new(
            device.clone(),
            PipelineDescriptorSetLayoutCreateInfo::from_stages(&stages)
                .into_pipeline_layout_create_info(device.clone())
                .unwrap(),
        )
        .unwrap();

        let subpass = Subpass::from(render_pass.clone(), 0).unwrap();

        let mut rasterization_state = RasterizationState::default();
        rasterization_state.cull_mode = CullMode::Back;

        let pipeline = GraphicsPipeline::new(
            device.clone(),
            None,
            GraphicsPipelineCreateInfo {
                stages: stages.into_iter().collect(),
                vertex_input_state: Some(vertex_input_state),
                input_assembly_state: Some(InputAssemblyState::default()),
                viewport_state: Some(ViewportState::default()),
                rasterization_state: Some(rasterization_state),
                multisample_state: Some(MultisampleState::default()),
                color_blend_state: Some(ColorBlendState::with_attachment_states(
                    subpass.num_color_attachments(),
                    ColorBlendAttachmentState::default(),
                )),
                dynamic_state: [DynamicState::Viewport].into_iter().collect(),
                subpass: Some(subpass.into()),
                ..GraphicsPipelineCreateInfo::layout(layout)
            },
        )
        .unwrap();

        let mut viewport = Viewport {
            offset: [0.0, 0.0],
            extent: window.inner_size().into(),
            depth_range: 0.0..=1.0,
        };

        let framebuffers = window_size_dependent_setup(&images, render_pass.clone(), &mut viewport);

        let camera = Camera::new();

        let app = App {
            instance,
            device: device.clone(),
            queue,
            surface,
            swapchain,
            images,
            render_pass,
            framebuffers,
            pipeline,
            vertex_buffer,
            viewport,
            command_buffer_allocator,
            memory_allocator,
            recreate_swapchain: false,
            previous_frame_end: Some(sync::now(device.clone()).boxed()),
            camera,
            keys_pressed: std::collections::HashSet::new(),
            last_frame_time: Instant::now(),
            cursor_locked: true,
        };

        (app, window)
    }

    fn handle_window_event(&mut self, event: WindowEvent, _window: &Window) -> bool {
        match event {
            WindowEvent::CloseRequested => true,
            WindowEvent::Resized(_) => {
                self.recreate_swapchain = true;
                false
            }
            WindowEvent::KeyboardInput { input, .. } => {
                if let Some(keycode) = input.virtual_keycode {
                    match input.state {
                        ElementState::Pressed => {
                            self.keys_pressed.insert(keycode);
                            // ESC to exit
                            if keycode == VirtualKeyCode::Escape {
                                return true;
                            }
                        }
                        ElementState::Released => {
                            self.keys_pressed.remove(&keycode);
                        }
                    }
                }
                false
            }
            _ => false,
        }
    }

    fn handle_device_event(&mut self, event: DeviceEvent) {
        match event {
            DeviceEvent::MouseMotion { delta } => {
                if self.cursor_locked {
                    self.camera.update_rotation(delta.0 as f32, delta.1 as f32);
                }
            }
            _ => {}
        }
    }

    fn update(&mut self) {
        let current_time = Instant::now();
        let delta_time = (current_time - self.last_frame_time).as_secs_f32();
        self.last_frame_time = current_time;

        // Calculate movement based on keys pressed
        let mut forward = 0.0;
        let mut right = 0.0;
        let mut up = 0.0;

        if self.keys_pressed.contains(&VirtualKeyCode::W) {
            forward += 1.0;
        }
        if self.keys_pressed.contains(&VirtualKeyCode::S) {
            forward -= 1.0;
        }
        if self.keys_pressed.contains(&VirtualKeyCode::D) {
            right += 1.0;
        }
        if self.keys_pressed.contains(&VirtualKeyCode::A) {
            right -= 1.0;
        }
        if self.keys_pressed.contains(&VirtualKeyCode::Space) {
            up += 1.0;
        }
        if self.keys_pressed.contains(&VirtualKeyCode::LControl) || 
           self.keys_pressed.contains(&VirtualKeyCode::RControl) {
            up -= 1.0;
        }

        self.camera.update_position(delta_time, forward, right, up);
    }

    fn render(&mut self, window: &Window) {
        let image_extent: [u32; 2] = window.inner_size().into();

        if image_extent.contains(&0) {
            return;
        }

        self.previous_frame_end.as_mut().unwrap().cleanup_finished();

        if self.recreate_swapchain {
            let (new_swapchain, new_images) = self
                .swapchain
                .recreate(SwapchainCreateInfo {
                    image_extent,
                    ..self.swapchain.create_info()
                })
                .expect("failed to recreate swapchain");

            self.swapchain = new_swapchain;
            self.viewport.extent = image_extent.map(|e| e as f32);
            self.framebuffers = window_size_dependent_setup(
                &new_images,
                self.render_pass.clone(),
                &mut self.viewport,
            );
            self.images = new_images;
            self.recreate_swapchain = false;
        }

        let (image_index, suboptimal, acquire_future) =
            match acquire_next_image(self.swapchain.clone(), None).map_err(Validated::unwrap) {
                Ok(r) => r,
                Err(VulkanError::OutOfDate) => {
                    self.recreate_swapchain = true;
                    return;
                }
                Err(e) => panic!("failed to acquire next image: {e}"),
            };

        if suboptimal {
            self.recreate_swapchain = true;
        }

        // Calculate MVP matrix
        let aspect_ratio = self.viewport.extent[0] / self.viewport.extent[1];
        let mvp = self.camera.get_mvp_matrix(aspect_ratio);
        let push_constants = vertex_shader::PushConstants {
            mvp: mvp.to_cols_array_2d(),
        };

        let mut builder = AutoCommandBufferBuilder::primary(
            &self.command_buffer_allocator,
            self.queue.queue_family_index(),
            CommandBufferUsage::OneTimeSubmit,
        )
        .unwrap();

        builder
            .begin_render_pass(
                RenderPassBeginInfo {
                    clear_values: vec![Some([0.53, 0.81, 0.92, 1.0].into())], // Sky blue
                    ..RenderPassBeginInfo::framebuffer(
                        self.framebuffers[image_index as usize].clone(),
                    )
                },
                SubpassBeginInfo {
                    contents: SubpassContents::Inline,
                    ..Default::default()
                },
            )
            .unwrap()
            .set_viewport(0, [self.viewport.clone()].into_iter().collect())
            .unwrap()
            .bind_pipeline_graphics(self.pipeline.clone())
            .unwrap()
            .push_constants(self.pipeline.layout().clone(), 0, push_constants)
            .unwrap()
            .bind_vertex_buffers(0, self.vertex_buffer.clone())
            .unwrap()
            .draw(self.vertex_buffer.len() as u32, 1, 0, 0)
            .unwrap()
            .end_render_pass(SubpassEndInfo::default())
            .unwrap();

        let command_buffer = builder.build().unwrap();

        let future = self
            .previous_frame_end
            .take()
            .unwrap()
            .join(acquire_future)
            .then_execute(self.queue.clone(), command_buffer)
            .unwrap()
            .then_swapchain_present(
                self.queue.clone(),
                SwapchainPresentInfo::swapchain_image_index(self.swapchain.clone(), image_index),
            )
            .then_signal_fence_and_flush();

        match future.map_err(Validated::unwrap) {
            Ok(future) => {
                self.previous_frame_end = Some(future.boxed());
            }
            Err(VulkanError::OutOfDate) => {
                self.recreate_swapchain = true;
                self.previous_frame_end = Some(sync::now(self.device.clone()).boxed());
            }
            Err(e) => {
                panic!("failed to flush future: {e}");
            }
        }
    }
}

fn window_size_dependent_setup(
    images: &[Arc<Image>],
    render_pass: Arc<RenderPass>,
    viewport: &mut Viewport,
) -> Vec<Arc<Framebuffer>> {
    let extent = images[0].extent();
    viewport.extent = [extent[0] as f32, extent[1] as f32];

    images
        .iter()
        .map(|image| {
            let view = ImageView::new_default(image.clone()).unwrap();
            Framebuffer::new(
                render_pass.clone(),
                FramebufferCreateInfo {
                    attachments: vec![view],
                    ..Default::default()
                },
            )
            .unwrap()
        })
        .collect::<Vec<_>>()
}

fn main() {
    let event_loop = EventLoop::new();

    let library = VulkanLibrary::new().expect("no local Vulkan library/DLL");

    let required_extensions = Surface::required_extensions(&event_loop);

    let instance = Instance::new(
        library,
        InstanceCreateInfo {
            flags: InstanceCreateFlags::ENUMERATE_PORTABILITY,
            enabled_extensions: required_extensions,
            ..Default::default()
        },
    )
    .expect("failed to create instance");

    let device_extensions = DeviceExtensions {
        khr_swapchain: true,
        ..DeviceExtensions::empty()
    };

    let (physical_device, queue_family_index) = instance
        .enumerate_physical_devices()
        .expect("could not enumerate devices")
        .filter(|p| p.supported_extensions().contains(&device_extensions))
        .filter_map(|p| {
            p.queue_family_properties()
                .iter()
                .enumerate()
                .position(|(_, q)| {
                    q.queue_flags.intersects(QueueFlags::GRAPHICS)
                })
                .map(|i| (p, i as u32))
        })
        .min_by_key(|(p, _)| match p.properties().device_type {
            PhysicalDeviceType::DiscreteGpu => 0,
            PhysicalDeviceType::IntegratedGpu => 1,
            PhysicalDeviceType::VirtualGpu => 2,
            PhysicalDeviceType::Cpu => 3,
            PhysicalDeviceType::Other => 4,
            _ => 5,
        })
        .expect("no suitable physical device found");

    println!(
        "Using device: {} (type: {:?})",
        physical_device.properties().device_name,
        physical_device.properties().device_type,
    );

    let (device, mut queues) = Device::new(
        physical_device,
        DeviceCreateInfo {
            enabled_extensions: device_extensions,
            queue_create_infos: vec![QueueCreateInfo {
                queue_family_index,
                ..Default::default()
            }],
            ..Default::default()
        },
    )
    .expect("failed to create device");

    let queue = queues.next().unwrap();

    let (mut app, window) = App::new(&event_loop, instance, device, queue);

    event_loop.run(move |event, _, control_flow| {
        match event {
            Event::WindowEvent { event, .. } => {
                if app.handle_window_event(event, &window) {
                    *control_flow = ControlFlow::Exit;
                } else {
                    *control_flow = ControlFlow::Poll;
                }
            }
            Event::DeviceEvent { event, .. } => {
                app.handle_device_event(event);
            }
            Event::RedrawRequested(_) => {
                app.update();
                app.render(&window);
            }
            Event::MainEventsCleared => {
                window.request_redraw();
            }
            _ => {
                *control_flow = ControlFlow::Poll;
            }
        }
    });
}
