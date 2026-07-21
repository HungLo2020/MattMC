use std::fs::{self, File};
use std::io::Write;

use zip::write::FileOptions;

#[test]
fn directory_pack_indexes_namespaces_and_reads_bytes() {
    let root = temp_root("dir_pack");
    write(&root.join("pack.mcmeta"), b"{}");
    write(
        &root.join("assets/minecraft/textures/block/stone.txt"),
        b"stone",
    );
    write(
        &root.join("assets/minecraft/textures/block/stone.txt.mcmeta"),
        b"meta",
    );
    write(&root.join("assets/example/binary/blob.bin"), &[0, 1, 0xff]);
    write(&root.join("assets/BAD/bad.txt"), b"bad");

    let (handle, stats) = super::open_directory(&root).expect("open");
    assert_eq!(stats.namespaces_indexed, 2);
    assert!(stats.entries_indexed >= 4);

    super::with_pack(handle, |pack| {
        assert_eq!(
            pack.list_namespaces("assets")?,
            vec!["example".to_string(), "minecraft".to_string()]
        );
        assert_eq!(
            pack.list_resources("assets", "minecraft", "textures")?,
            vec![
                "textures/block/stone.txt".to_string(),
                "textures/block/stone.txt.mcmeta".to_string(),
            ]
        );
        assert!(pack.list_resources("assets", "minecraft", "")?.is_empty());
        assert!(pack.exists("assets", "minecraft", "textures/block/stone.txt")?);
        assert_eq!(
            pack.read_resource("assets", "example", "binary/blob.bin")?
                .unwrap(),
            vec![0, 1, 0xff]
        );
        assert_eq!(
            pack.read_root_resource("pack.mcmeta")?.unwrap(),
            b"{}".to_vec()
        );
        Ok(())
    })
    .expect("ops");

    super::close(handle).expect("close");
    assert!(super::with_pack(handle, |_| Ok(())).is_err());
}

#[test]
fn zip_pack_indexes_with_prefix_and_rejects_traversal_entries() {
    let root = temp_root("zip_pack");
    let zip_path = root.join("pack.zip");
    create_zip(
        &zip_path,
        &[
            ("pack.mcmeta", b"{}".as_slice()),
            ("overlay/assets/minecraft/a.txt", b"a"),
            ("overlay/assets/example/nested/b.bin", &[1, 2, 3]),
            ("overlay/assets/minecraft/bad/../escape.txt", b"nope"),
        ],
    );

    let (handle, _) = super::open_zip(&zip_path, "overlay").expect("open");
    super::with_pack(handle, |pack| {
        assert_eq!(
            pack.list_namespaces("assets")?,
            vec!["example".to_string(), "minecraft".to_string()]
        );
        assert_eq!(
            pack.list_resources("assets", "minecraft", "a")?,
            vec!["a.txt".to_string()]
        );
        assert!(pack.list_resources("assets", "minecraft", "")?.is_empty());
        assert_eq!(
            pack.read_resource("assets", "example", "nested/b.bin")?
                .unwrap(),
            vec![1, 2, 3]
        );
        assert!(pack
            .exists("assets", "minecraft", "bad/../escape.txt")
            .is_err());
        Ok(())
    })
    .expect("ops");
    super::close(handle).expect("close");
}

#[test]
fn zip_handle_is_stale_after_close_and_replacement() {
    let root = temp_root("zip_pack_replace");
    let zip_path = root.join("pack.zip");
    create_zip(
        &zip_path,
        &[("assets/minecraft/validation/value.txt", b"old".as_slice())],
    );

    let (old_handle, _) = super::open_zip(&zip_path, "").expect("open old");
    super::close(old_handle).expect("close old");
    create_zip(
        &zip_path,
        &[("assets/minecraft/validation/value.txt", b"new".as_slice())],
    );

    let (new_handle, _) = super::open_zip(&zip_path, "").expect("open new");
    assert_ne!(old_handle, new_handle);
    assert!(super::with_pack(old_handle, |pack| {
        pack.read_resource("assets", "minecraft", "validation/value.txt")
            .map(|_| ())
    })
    .is_err());
    super::with_pack(new_handle, |pack| {
        assert_eq!(
            pack.read_resource("assets", "minecraft", "validation/value.txt")?
                .unwrap(),
            b"new".to_vec()
        );
        Ok(())
    })
    .expect("new handle reads replacement");
    super::close(new_handle).expect("close new");
}

fn temp_root(name: &str) -> std::path::PathBuf {
    let mut root = std::env::temp_dir();
    root.push(format!(
        "mattmc_pack_test_{}_{}_{}",
        name,
        std::process::id(),
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos()
    ));
    fs::create_dir_all(&root).unwrap();
    root
}

fn write(path: &std::path::Path, bytes: &[u8]) {
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(path, bytes).unwrap();
}

fn create_zip(path: &std::path::Path, entries: &[(&str, &[u8])]) {
    let file = File::create(path).unwrap();
    let mut zip = zip::ZipWriter::new(file);
    let options = FileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    for (name, bytes) in entries {
        zip.start_file(*name, options).unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap();
}
