//! SNBT, as the item catalog stores default components (the save form
//! `Tag.toString` prints), parsed into NBT for Java to wrap as tags: compounds,
//! lists, typed arrays (`[B;`, `[I;`, `[L;`), quoted and unquoted strings,
//! numbers with `b s l f d` suffixes (plain integers are ints, plain decimals
//! doubles) and `true`/`false` as bytes.

#[derive(Clone, Debug, PartialEq)]
pub enum Nbt {
    Byte(i8),
    Short(i16),
    Int(i32),
    Long(i64),
    Float(f32),
    Double(f64),
    ByteArray(Vec<i8>),
    String(String),
    List(Vec<Nbt>),
    Compound(Vec<(String, Nbt)>),
    IntArray(Vec<i32>),
    LongArray(Vec<i64>),
}

pub fn parse(text: &str) -> Result<Nbt, String> {
    let mut p = Parser { s: text.as_bytes(), pos: 0 };
    let value = p.value()?;
    p.ws();
    if p.pos != p.s.len() {
        return Err(format!("trailing data at {} in {text}", p.pos));
    }
    Ok(value)
}

struct Parser<'a> {
    s: &'a [u8],
    pos: usize,
}

fn unquoted_char(c: u8) -> bool {
    c.is_ascii_alphanumeric() || matches!(c, b'_' | b'-' | b'.' | b'+')
}

impl Parser<'_> {
    fn ws(&mut self) {
        while self.pos < self.s.len() && self.s[self.pos].is_ascii_whitespace() {
            self.pos += 1;
        }
    }
    fn peek(&self) -> Option<u8> {
        self.s.get(self.pos).copied()
    }
    fn expect(&mut self, c: u8) -> Result<(), String> {
        self.ws();
        if self.peek() == Some(c) {
            self.pos += 1;
            Ok(())
        } else {
            Err(format!("expected '{}' at {}", c as char, self.pos))
        }
    }
    fn value(&mut self) -> Result<Nbt, String> {
        self.ws();
        match self.peek() {
            Some(b'{') => self.compound(),
            Some(b'[') => self.list(),
            Some(b'"' | b'\'') => Ok(Nbt::String(self.quoted()?)),
            Some(_) => self.scalar(),
            None => Err("unexpected end".into()),
        }
    }
    fn compound(&mut self) -> Result<Nbt, String> {
        self.pos += 1;
        let mut entries: Vec<(String, Nbt)> = Vec::new();
        self.ws();
        if self.peek() == Some(b'}') {
            self.pos += 1;
            return Ok(Nbt::Compound(entries));
        }
        loop {
            self.ws();
            let key = match self.peek() {
                Some(b'"' | b'\'') => self.quoted()?,
                _ => self.unquoted()?,
            };
            self.expect(b':')?;
            let value = self.value()?;
            match entries.iter_mut().find(|(k, _)| *k == key) {
                Some(existing) => existing.1 = value,
                None => entries.push((key, value)),
            }
            self.ws();
            match self.peek() {
                Some(b',') => self.pos += 1,
                Some(b'}') => {
                    self.pos += 1;
                    return Ok(Nbt::Compound(entries));
                }
                _ => return Err(format!("unterminated compound at {}", self.pos)),
            }
        }
    }
    fn list(&mut self) -> Result<Nbt, String> {
        self.pos += 1;
        if let (Some(kind @ (b'B' | b'I' | b'L')), Some(b';')) = (self.peek(), self.s.get(self.pos + 1).copied()) {
            self.pos += 2;
            let mut items = Vec::new();
            self.ws();
            if self.peek() != Some(b']') {
                loop {
                    items.push(self.scalar()?);
                    self.ws();
                    match self.peek() {
                        Some(b',') => self.pos += 1,
                        Some(b']') => break,
                        _ => return Err(format!("unterminated array at {}", self.pos)),
                    }
                }
            }
            self.pos += 1;
            let ints = |items: Vec<Nbt>| -> Vec<i64> {
                items.into_iter().map(|n| match n {
                    Nbt::Byte(v) => i64::from(v),
                    Nbt::Short(v) => i64::from(v),
                    Nbt::Int(v) => i64::from(v),
                    Nbt::Long(v) => v,
                    _ => 0,
                }).collect()
            };
            return Ok(match kind {
                b'B' => Nbt::ByteArray(ints(items).into_iter().map(|v| v as i8).collect()),
                b'I' => Nbt::IntArray(ints(items).into_iter().map(|v| v as i32).collect()),
                _ => Nbt::LongArray(ints(items)),
            });
        }
        let mut items = Vec::new();
        self.ws();
        if self.peek() == Some(b']') {
            self.pos += 1;
            return Ok(Nbt::List(items));
        }
        loop {
            items.push(self.value()?);
            self.ws();
            match self.peek() {
                Some(b',') => self.pos += 1,
                Some(b']') => {
                    self.pos += 1;
                    return Ok(Nbt::List(items));
                }
                _ => return Err(format!("unterminated list at {}", self.pos)),
            }
        }
    }
    fn quoted(&mut self) -> Result<String, String> {
        let quote = self.s[self.pos];
        self.pos += 1;
        let mut out = Vec::new();
        loop {
            let Some(c) = self.peek() else { return Err("unterminated string".into()) };
            self.pos += 1;
            if c == quote {
                return String::from_utf8(out).map_err(|e| e.to_string());
            }
            if c == b'\\' {
                let Some(e) = self.peek() else { return Err("unterminated escape".into()) };
                self.pos += 1;
                match e {
                    b'n' => out.push(b'\n'),
                    b't' => out.push(b'\t'),
                    b'r' => out.push(b'\r'),
                    b'b' => out.push(8),
                    b'f' => out.push(12),
                    b's' => out.push(b' '),
                    b'x' | b'u' | b'U' => {
                        let n = match e { b'x' => 2, b'u' => 4, _ => 8 };
                        let hex = std::str::from_utf8(self.s.get(self.pos..self.pos + n).ok_or("short escape")?).map_err(|e| e.to_string())?;
                        let code = u32::from_str_radix(hex, 16).map_err(|e| e.to_string())?;
                        self.pos += n;
                        let mut buf = [0; 4];
                        out.extend_from_slice(char::from_u32(code).ok_or("bad escape")?.encode_utf8(&mut buf).as_bytes());
                    }
                    other => out.push(other),
                }
            } else {
                out.push(c);
            }
        }
    }
    fn unquoted(&mut self) -> Result<String, String> {
        let start = self.pos;
        while self.peek().is_some_and(unquoted_char) {
            self.pos += 1;
        }
        if start == self.pos {
            return Err(format!("expected a name at {}", self.pos));
        }
        Ok(String::from_utf8_lossy(&self.s[start..self.pos]).into_owned())
    }
    fn scalar(&mut self) -> Result<Nbt, String> {
        self.ws();
        if matches!(self.peek(), Some(b'"' | b'\'')) {
            return Ok(Nbt::String(self.quoted()?));
        }
        let token = self.unquoted()?;
        Ok(number(&token).unwrap_or_else(|| match token.as_str() {
            "true" => Nbt::Byte(1),
            "false" => Nbt::Byte(0),
            _ => Nbt::String(token),
        }))
    }
}

fn number(token: &str) -> Option<Nbt> {
    let (body, suffix) = match token.as_bytes().last()? {
        c @ (b'b' | b'B' | b's' | b'S' | b'l' | b'L' | b'f' | b'F' | b'd' | b'D') if token.len() > 1 => (&token[..token.len() - 1], Some(c.to_ascii_lowercase())),
        _ => (token, None),
    };
    let integral = !body.contains(['.', 'e', 'E']);
    Some(match suffix {
        Some(b'b') if integral => Nbt::Byte(body.parse().ok()?),
        Some(b's') if integral => Nbt::Short(body.parse().ok()?),
        Some(b'l') if integral => Nbt::Long(body.parse().ok()?),
        Some(b'f') => Nbt::Float(body.parse().ok()?),
        Some(b'd') => Nbt::Double(body.parse().ok()?),
        None if integral => Nbt::Int(body.parse().ok()?),
        None => Nbt::Double(body.parse().ok()?),
        _ => return None,
    })
}

/// Binary form for Java: NBT tag ID, then the payload (little endian;
/// strings and counts as u32 length + bytes / items; list items carry their own ID).
pub fn write(nbt: &Nbt, out: &mut Vec<u8>) {
    let u32le = |out: &mut Vec<u8>, v: usize| out.extend_from_slice(&(v as u32).to_le_bytes());
    match nbt {
        Nbt::Byte(v) => {
            out.push(1);
            out.push(*v as u8);
        }
        Nbt::Short(v) => {
            out.push(2);
            out.extend_from_slice(&v.to_le_bytes());
        }
        Nbt::Int(v) => {
            out.push(3);
            out.extend_from_slice(&v.to_le_bytes());
        }
        Nbt::Long(v) => {
            out.push(4);
            out.extend_from_slice(&v.to_le_bytes());
        }
        Nbt::Float(v) => {
            out.push(5);
            out.extend_from_slice(&v.to_bits().to_le_bytes());
        }
        Nbt::Double(v) => {
            out.push(6);
            out.extend_from_slice(&v.to_bits().to_le_bytes());
        }
        Nbt::ByteArray(v) => {
            out.push(7);
            u32le(out, v.len());
            out.extend(v.iter().map(|b| *b as u8));
        }
        Nbt::String(s) => {
            out.push(8);
            u32le(out, s.len());
            out.extend_from_slice(s.as_bytes());
        }
        Nbt::List(items) => {
            out.push(9);
            u32le(out, items.len());
            items.iter().for_each(|i| write(i, out));
        }
        Nbt::Compound(entries) => {
            out.push(10);
            u32le(out, entries.len());
            for (k, v) in entries {
                u32le(out, k.len());
                out.extend_from_slice(k.as_bytes());
                write(v, out);
            }
        }
        Nbt::IntArray(v) => {
            out.push(11);
            u32le(out, v.len());
            v.iter().for_each(|i| out.extend_from_slice(&i.to_le_bytes()));
        }
        Nbt::LongArray(v) => {
            out.push(12);
            u32le(out, v.len());
            v.iter().for_each(|i| out.extend_from_slice(&i.to_le_bytes()));
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_the_save_form() {
        assert_eq!(parse("{a:1b,b:[1.5f,2.0d],c:\"x\\\"y\",d:[I;1,2],e:{}}").unwrap(), Nbt::Compound(vec![
            ("a".into(), Nbt::Byte(1)),
            ("b".into(), Nbt::List(vec![Nbt::Float(1.5), Nbt::Double(2.0)])),
            ("c".into(), Nbt::String("x\"y".into())),
            ("d".into(), Nbt::IntArray(vec![1, 2])),
            ("e".into(), Nbt::Compound(vec![])),
        ]));
        assert_eq!(parse("64").unwrap(), Nbt::Int(64));
        assert!(parse("{a:").is_err());
    }

    #[test]
    fn every_catalog_value_parses() {
        for def in super::super::definitions() {
            for (t, v) in def.components.iter().chain(super::super::common()) {
                parse(v).unwrap_or_else(|e| panic!("{} {t}: {e}", def.name));
            }
        }
    }
}
