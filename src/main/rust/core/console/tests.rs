use std::io::{Read, Write};
use std::os::fd::AsRawFd;
use std::os::unix::process::CommandExt;
use std::process::{ChildStdout, Command, Stdio};
use std::time::{Duration, Instant};

const READY: &[u8] = b"diagnostic-pipe-child-ready\n";

fn read_ready(stdout: &mut ChildStdout) -> std::io::Result<Vec<u8>> {
    let deadline = Instant::now() + Duration::from_secs(10);
    let mut bytes = Vec::new();
    while !bytes.ends_with(READY) {
        if Instant::now() >= deadline || bytes.len() >= 4096 {
            return Err(std::io::ErrorKind::TimedOut.into());
        }
        let mut descriptor = libc::pollfd {
            fd: stdout.as_raw_fd(),
            events: libc::POLLIN,
            revents: 0,
        };
        if unsafe { libc::poll(&mut descriptor, 1, 100) } < 0 {
            return Err(std::io::Error::last_os_error());
        }
        if descriptor.revents != 0 {
            let mut byte = [0];
            stdout.read_exact(&mut byte)?;
            bytes.push(byte[0]);
        }
    }
    Ok(bytes)
}

#[test]
fn diagnostic_stdio_does_not_abort_on_closed_pipe() {
    if std::env::var_os("MATTMC_TEST_DIAGNOSTIC_PIPE_CHILD").is_some() {
        super::stdout(format_args!("diagnostic-pipe-child-ready"));
        std::io::stdin().read_exact(&mut [0]).unwrap();
        super::stdout(format_args!(
            "vulkan.submission.ownership id={} pending={}",
            7, 0
        ));
        super::stderr(format_args!("vulkan.diagnostic error={}", "probe"));
        // Avoid libtest's own result printer on the deliberately closed pipe.
        std::process::exit(0);
    }
    for closed in ["none", "stdout", "stderr"] {
        let mut command = Command::new(std::env::current_exe().unwrap());
        command.args([
            "--exact",
            "core::console::tests::diagnostic_stdio_does_not_abort_on_closed_pipe",
            "--nocapture",
            "--quiet",
        ]);
        command
            .env("MATTMC_TEST_DIAGNOSTIC_PIPE_CHILD", "1")
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        // A failing before-fix child must not consume the gameplay core reserve.
        unsafe {
            command.pre_exec(|| {
                let limit = libc::rlimit {
                    rlim_cur: 0,
                    rlim_max: 0,
                };
                if libc::setrlimit(libc::RLIMIT_CORE, &limit) != 0 {
                    return Err(std::io::Error::last_os_error());
                }
                Ok(())
            });
        }
        let mut child = command.spawn().unwrap();
        if let Err(error) = read_ready(child.stdout.as_mut().unwrap()) {
            let _ = child.kill();
            let _ = child.wait();
            panic!("child diagnostic handshake failed: {error}");
        }
        if closed == "stdout" {
            drop(child.stdout.take());
        } else if closed == "stderr" {
            drop(child.stderr.take());
        }
        child.stdin.take().unwrap().write_all(b"1").unwrap();
        let deadline = Instant::now() + Duration::from_secs(10);
        while child.try_wait().unwrap().is_none() {
            if Instant::now() >= deadline {
                let _ = child.kill();
                let _ = child.wait();
                panic!("child diagnostic exceeded its deadline");
            }
            std::thread::sleep(Duration::from_millis(10));
        }
        let output = child.wait_with_output().unwrap();
        assert!(
            output.status.success(),
            "{closed}: {}",
            String::from_utf8_lossy(&output.stderr)
        );
        if closed != "stdout" {
            assert_eq!(
                output.stdout,
                b"vulkan.submission.ownership id=7 pending=0\n"
            );
        }
        if closed != "stderr" {
            assert_eq!(output.stderr, b"vulkan.diagnostic error=probe\n");
        }
    }
}
