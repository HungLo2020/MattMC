#[cfg(feature = "tracy")]
use std::sync::OnceLock;

#[cfg(feature = "tracy")]
static CLIENT: OnceLock<tracy_client::Client> = OnceLock::new();

pub(super) struct Zone {
    #[cfg(feature = "tracy")]
    _span: Option<tracy_client::Span>,
}

impl Zone {
    pub(super) fn new(name: &'static str) -> Self {
        #[cfg(not(feature = "tracy"))]
        let _ = name;
        if !enabled() {
            return Self {
                #[cfg(feature = "tracy")]
                _span: None,
            };
        }
        #[cfg(feature = "tracy")]
        {
            let client = CLIENT.get_or_init(|| {
                let client = tracy_client::Client::start();
                eprintln!("MattMC Rust VulkanicGAL OpenGL Tracy client start requested");
                client.set_thread_name("MattMC Rust VulkanicGAL OpenGL");
                client.message("MattMC Rust VulkanicGAL OpenGL Tracy client started", 0);
                client
            });
            client.message(name, 0);
            return Self {
                _span: Some(client.clone().span_alloc(
                    Some(name),
                    "render::vulkanic::backends::opengl",
                    file!(),
                    line!(),
                    0,
                )),
            };
        }
        #[cfg(not(feature = "tracy"))]
        Self {}
    }
}

#[allow(dead_code)]
pub(super) fn message(message: &str) {
    #[cfg(not(feature = "tracy"))]
    let _ = message;
    if !enabled() {
        return;
    }
    #[cfg(feature = "tracy")]
    CLIENT
        .get_or_init(tracy_client::Client::start)
        .message(message, 0);
}

fn enabled() -> bool {
    std::env::var("MATTMC_RUST_TRACY")
        .map(|value| value == "1" || value.eq_ignore_ascii_case("true"))
        .unwrap_or(false)
}
