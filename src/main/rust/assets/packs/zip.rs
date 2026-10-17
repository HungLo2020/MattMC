//! A read-only ZIP archive (`java.util.zip.ZipFile` for resource packs).
//!
//! The central directory is read once. Entry names decode as UTF-8, as
//! `ZipFile`'s default charset does (a malformed name rejects the archive,
//! as Java's does); a duplicated name resolves to its last
//! entry, as Java's entry table does. Entries are read with positional reads,
//! so concurrent readers need no lock. Stored and deflated entries are
//! supported; Java rejects other methods too.

use std::collections::HashMap;
use std::fs::File;
use std::io::{self, Read};
use std::path::Path;

use flate2::read::DeflateDecoder;

const EOCD_SIGNATURE: u32 = 0x0605_4b50;
const ZIP64_EOCD_LOCATOR_SIGNATURE: u32 = 0x0706_4b50;
const ZIP64_EOCD_SIGNATURE: u32 = 0x0606_4b50;
const CENTRAL_SIGNATURE: u32 = 0x0201_4b50;
const LOCAL_SIGNATURE: u32 = 0x0403_4b50;
const LOCAL_HEADER_LEN: u64 = 30;
const METHOD_STORED: u16 = 0;
const METHOD_DEFLATED: u16 = 8;
/// The EOCD record plus the longest comment.
const EOCD_SEARCH: u64 = 22 + 0xFFFF;

#[derive(Debug, Clone)]
pub(crate) struct Entry {
    pub(crate) name: Box<str>,
    method: u16,
    compressed_size: u64,
    size: u64,
    local_offset: u64,
}

impl Entry {
    /// `ZipEntry.isDirectory`: the name ends with `/`.
    pub(crate) fn is_directory(&self) -> bool {
        self.name.ends_with('/')
    }
}

#[derive(Debug)]
pub(crate) struct Archive {
    file: File,
    entries: Vec<Entry>,
    by_name: HashMap<Box<str>, u32>,
}

fn u16_at(bytes: &[u8], offset: usize) -> u16 {
    u16::from_le_bytes([bytes[offset], bytes[offset + 1]])
}

fn u32_at(bytes: &[u8], offset: usize) -> u32 {
    u32::from_le_bytes(bytes[offset..offset + 4].try_into().unwrap())
}

fn u64_at(bytes: &[u8], offset: usize) -> u64 {
    u64::from_le_bytes(bytes[offset..offset + 8].try_into().unwrap())
}

fn invalid(message: impl Into<String>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message.into())
}

#[cfg(unix)]
fn read_at(file: &File, buffer: &mut [u8], offset: u64) -> io::Result<()> {
    use std::os::unix::fs::FileExt;
    file.read_exact_at(buffer, offset)
}

#[cfg(windows)]
fn read_at(file: &File, mut buffer: &mut [u8], mut offset: u64) -> io::Result<()> {
    use std::os::windows::fs::FileExt;
    while !buffer.is_empty() {
        match file.seek_read(buffer, offset) {
            Ok(0) => return Err(io::Error::from(io::ErrorKind::UnexpectedEof)),
            Ok(read) => {
                buffer = &mut buffer[read..];
                offset += read as u64;
            }
            Err(error) if error.kind() == io::ErrorKind::Interrupted => {}
            Err(error) => return Err(error),
        }
    }
    Ok(())
}

/// A positional reader over one entry's bytes.
struct PositionalReader<'a> {
    file: &'a File,
    offset: u64,
    remaining: u64,
}

impl Read for PositionalReader<'_> {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        if self.remaining == 0 {
            return Ok(0);
        }
        let length = buffer.len().min(self.remaining.min(1 << 20) as usize);
        read_at(self.file, &mut buffer[..length], self.offset)?;
        self.offset += length as u64;
        self.remaining -= length as u64;
        Ok(length)
    }
}

impl Archive {
    pub(crate) fn open(path: &Path) -> io::Result<Archive> {
        let file = File::open(path)?;
        let length = file.metadata()?.len();
        if length < 22 {
            return Err(invalid("zip file is empty"));
        }
        let search = EOCD_SEARCH.min(length);
        let mut tail = vec![0u8; search as usize];
        read_at(&file, &mut tail, length - search)?;
        let eocd = (0..=tail.len() - 22)
            .rev()
            .find(|&at| u32_at(&tail, at) == EOCD_SIGNATURE)
            .ok_or_else(|| invalid("zip END header not found"))?;
        let mut count = u16_at(&tail, eocd + 10) as u64;
        let mut directory_size = u32_at(&tail, eocd + 12) as u64;
        let mut directory_offset = u32_at(&tail, eocd + 16) as u64;
        let eocd_position = length - search + eocd as u64;
        if count == 0xFFFF || directory_size == 0xFFFF_FFFF || directory_offset == 0xFFFF_FFFF {
            if eocd_position >= 20 {
                let mut locator = [0u8; 20];
                read_at(&file, &mut locator, eocd_position - 20)?;
                if u32_at(&locator, 0) == ZIP64_EOCD_LOCATOR_SIGNATURE {
                    let mut record = [0u8; 56];
                    read_at(&file, &mut record, u64_at(&locator, 8))?;
                    if u32_at(&record, 0) != ZIP64_EOCD_SIGNATURE {
                        return Err(invalid("invalid zip64 END header"));
                    }
                    count = u64_at(&record, 32);
                    directory_size = u64_at(&record, 40);
                    directory_offset = u64_at(&record, 48);
                }
            }
        }
        if directory_offset.checked_add(directory_size).is_none_or(|end| end > length) {
            return Err(invalid("invalid END header (bad central directory offset)"));
        }
        let mut directory = vec![0u8; directory_size as usize];
        read_at(&file, &mut directory, directory_offset)?;
        let mut entries = Vec::with_capacity(count.min(1 << 20) as usize);
        let mut by_name = HashMap::with_capacity(entries.capacity());
        let mut at = 0usize;
        while at + 46 <= directory.len() && (entries.len() as u64) < count {
            if u32_at(&directory, at) != CENTRAL_SIGNATURE {
                return Err(invalid("invalid CEN header (bad signature)"));
            }
            let flags = u16_at(&directory, at + 8);
            let method = u16_at(&directory, at + 10);
            let mut compressed_size = u32_at(&directory, at + 20) as u64;
            let mut size = u32_at(&directory, at + 24) as u64;
            let name_length = u16_at(&directory, at + 28) as usize;
            let extra_length = u16_at(&directory, at + 30) as usize;
            let comment_length = u16_at(&directory, at + 32) as usize;
            let mut local_offset = u32_at(&directory, at + 42) as u64;
            let name_start = at + 46;
            let extra_start = name_start + name_length;
            let next = extra_start + extra_length + comment_length;
            if next > directory.len() {
                return Err(invalid("invalid CEN header (bad header size)"));
            }
            if flags & 1 != 0 {
                return Err(invalid("invalid CEN header (encrypted entry)"));
            }
            let name = std::str::from_utf8(&directory[name_start..extra_start])
                .map_err(|_| invalid("invalid CEN header (bad entry name)"))?
                .to_owned()
                .into_boxed_str();
            // ZIP64 extra field: the 0xFFFFFFFF fields follow in order.
            let mut extra = extra_start;
            let extra_end = extra_start + extra_length;
            while extra + 4 <= extra_end {
                let tag = u16_at(&directory, extra);
                let data_length = u16_at(&directory, extra + 2) as usize;
                let data = extra + 4;
                if data + data_length > extra_end {
                    break;
                }
                if tag == 0x0001 {
                    let mut field = data;
                    let mut take = |value: &mut u64| {
                        if *value == 0xFFFF_FFFF && field + 8 <= data + data_length {
                            *value = u64_at(&directory, field);
                            field += 8;
                        }
                    };
                    take(&mut size);
                    take(&mut compressed_size);
                    take(&mut local_offset);
                }
                extra = data + data_length;
            }
            let index = entries.len() as u32;
            by_name.insert(name.clone(), index);
            entries.push(Entry { name, method, compressed_size, size, local_offset });
            at = next;
        }
        Ok(Archive { file, entries, by_name })
    }

    /// Entries in central directory order (`ZipFile.entries()`).
    pub(crate) fn entries(&self) -> &[Entry] {
        &self.entries
    }

    /// `ZipFile.getEntry`: the exact name, else the name as a directory.
    pub(crate) fn find(&self, name: &str) -> Option<u32> {
        if let Some(&index) = self.by_name.get(name) {
            return Some(index);
        }
        if name.ends_with('/') {
            return None;
        }
        let mut directory = String::with_capacity(name.len() + 1);
        directory.push_str(name);
        directory.push('/');
        self.by_name.get(directory.as_str()).copied()
    }

    /// Reads and inflates one entry.
    pub(crate) fn read(&self, index: u32) -> io::Result<Vec<u8>> {
        let entry = self.entries.get(index as usize).ok_or_else(|| invalid("no such zip entry"))?;
        let mut header = [0u8; LOCAL_HEADER_LEN as usize];
        read_at(&self.file, &mut header, entry.local_offset)?;
        if u32_at(&header, 0) != LOCAL_SIGNATURE {
            return Err(invalid(format!("invalid LOC header (bad signature) for {}", entry.name)));
        }
        let data_offset = entry.local_offset
            + LOCAL_HEADER_LEN
            + u16_at(&header, 26) as u64
            + u16_at(&header, 28) as u64;
        let reader = PositionalReader { file: &self.file, offset: data_offset, remaining: entry.compressed_size };
        let mut bytes = Vec::with_capacity(entry.size.min(1 << 30) as usize);
        match entry.method {
            METHOD_STORED => {
                let mut reader = reader;
                reader.read_to_end(&mut bytes)?;
            }
            METHOD_DEFLATED => {
                DeflateDecoder::new(reader).read_to_end(&mut bytes)?;
            }
            method => return Err(invalid(format!("invalid compression method {method} for {}", entry.name))),
        }
        Ok(bytes)
    }
}
