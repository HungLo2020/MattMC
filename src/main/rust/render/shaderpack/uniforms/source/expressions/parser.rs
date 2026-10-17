//! Bounded syntax parser for the Iris custom-property expression language.

use super::*;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) enum Op {
    Add,
    Sub,
    Mul,
    Div,
    Rem,
    Eq,
    Ne,
    Lt,
    Le,
    Gt,
    Ge,
    And,
    Or,
}

impl Op {
    fn priority(self) -> u8 {
        // Frozen IrisOptions gives && and || the same priority, and likewise
        // all comparisons. Do not substitute GLSL/C operator precedence.
        match self {
            Self::And | Self::Or => 1,
            Self::Eq | Self::Ne | Self::Lt | Self::Le | Self::Gt | Self::Ge => 2,
            Self::Add | Self::Sub => 3,
            Self::Mul | Self::Div | Self::Rem => 4,
        }
    }
}

#[derive(Debug)]
pub(super) enum Expr {
    Literal(Literal),
    Name(String),
    Neg(Box<Expr>),
    Not(Box<Expr>),
    Binary(Op, Box<Expr>, Box<Expr>),
    Call(String, Vec<Expr>),
}

#[derive(Clone, Debug)]
enum Token {
    Literal(Literal),
    Name(String),
    Op(Op),
    Not,
    Open,
    Close,
    Comma,
    End,
}

pub(super) fn parse(source: &str) -> GalResult<Expr> {
    let mut tokens = Vec::new();
    let bytes = source.as_bytes();
    let mut cursor = 0;
    while cursor < bytes.len() {
        let start = cursor;
        let byte = bytes[cursor];
        if byte.is_ascii_whitespace() {
            cursor += 1;
            continue;
        }
        let token = if byte.is_ascii_alphabetic() || byte == b'_' {
            cursor += 1;
            while cursor < bytes.len()
                && (bytes[cursor].is_ascii_alphanumeric() || matches!(bytes[cursor], b'_' | b'.'))
            {
                cursor += 1;
            }
            Token::Name(source[start..cursor].to_string())
        } else if byte.is_ascii_digit() || byte == b'.' {
            cursor += 1;
            // Frozen's tokenizer includes letters and dots in numeric tokens,
            // but treats signs as operators, including signs after `e`.
            while cursor < bytes.len()
                && (bytes[cursor].is_ascii_alphanumeric() || bytes[cursor] == b'.')
            {
                cursor += 1;
            }
            let number = &source[start..cursor];
            let integer = if let Some(binary) = number.strip_prefix("0b") {
                i32::from_str_radix(binary, 2)
            } else if let Some(hexadecimal) = number.strip_prefix("0x") {
                i32::from_str_radix(hexadecimal, 16)
            } else if number.len() > 1 && number.starts_with('0') {
                i32::from_str_radix(&number[1..], 8)
            } else {
                number.parse::<i32>()
            };
            let literal = match integer {
                Ok(value) => Literal::Int(value),
                Err(_) => {
                    let float = number.strip_suffix(['f', 'F', 'd', 'D']).unwrap_or(number);
                    let value: f32 = float
                        .parse()
                        .map_err(|_| invalid("invalid or unsupported custom expression number"))?;
                    if !value.is_finite() {
                        return Err(invalid("non-finite custom expression literal"));
                    }
                    Literal::Float(value.to_bits())
                }
            };
            Token::Literal(literal)
        } else {
            let two = bytes.get(cursor..cursor + 2);
            let op = match two {
                Some(b"&&") => Some(Op::And),
                Some(b"||") => Some(Op::Or),
                Some(b"==") => Some(Op::Eq),
                Some(b"!=") => Some(Op::Ne),
                Some(b"<=") => Some(Op::Le),
                Some(b">=") => Some(Op::Ge),
                _ => None,
            };
            if let Some(op) = op {
                cursor += 2;
                Token::Op(op)
            } else {
                cursor += 1;
                match byte {
                    b'+' => Token::Op(Op::Add),
                    b'-' => Token::Op(Op::Sub),
                    b'*' => Token::Op(Op::Mul),
                    b'/' => Token::Op(Op::Div),
                    b'%' => Token::Op(Op::Rem),
                    b'<' => Token::Op(Op::Lt),
                    b'>' => Token::Op(Op::Gt),
                    b'!' => Token::Not,
                    b'(' => Token::Open,
                    b')' => Token::Close,
                    b',' => Token::Comma,
                    _ => return Err(invalid("unsupported custom expression token")),
                }
            }
        };
        tokens.push(token);
        if tokens.len() > 2048 {
            return Err(invalid("custom expression token budget exceeded"));
        }
    }
    tokens.push(Token::End);
    let mut parser = Parser {
        tokens,
        cursor: 0,
        nodes: 0,
    };
    let expression = parser.expression(1, 0)?;
    if !matches!(parser.tokens[parser.cursor], Token::End) {
        return Err(invalid("trailing custom expression tokens"));
    }
    Ok(expression)
}

struct Parser {
    tokens: Vec<Token>,
    cursor: usize,
    nodes: usize,
}

impl Parser {
    fn expression(&mut self, minimum: u8, depth: usize) -> GalResult<Expr> {
        if depth > 64 {
            return Err(invalid("custom expression syntax depth exceeded"));
        }
        self.nodes += 1;
        if self.nodes > 1024 {
            return Err(invalid("custom expression syntax node budget exceeded"));
        }
        let token = self.tokens[self.cursor].clone();
        self.cursor += 1;
        let mut left = match token {
            Token::Literal(value) => Expr::Literal(value),
            Token::Name(name) => {
                if matches!(self.tokens[self.cursor], Token::Open) {
                    self.cursor += 1;
                    let mut arguments = Vec::new();
                    if !matches!(self.tokens[self.cursor], Token::Close) {
                        loop {
                            arguments.push(self.expression(1, depth + 1)?);
                            if arguments.len() > 65 {
                                return Err(invalid("custom expression argument budget exceeded"));
                            }
                            if !matches!(self.tokens[self.cursor], Token::Comma) {
                                break;
                            }
                            self.cursor += 1;
                        }
                    }
                    self.close()?;
                    Expr::Call(name, arguments)
                } else {
                    Expr::Name(name)
                }
            }
            Token::Op(Op::Sub) => Expr::Neg(Box::new(self.expression(5, depth + 1)?)),
            Token::Not => Expr::Not(Box::new(self.expression(5, depth + 1)?)),
            Token::Open => {
                let value = self.expression(1, depth + 1)?;
                self.close()?;
                value
            }
            _ => return Err(invalid("custom expression requires a value")),
        };
        while let Token::Op(op) = self.tokens[self.cursor] {
            if op.priority() < minimum {
                break;
            }
            self.cursor += 1;
            let right = self.expression(op.priority() + 1, depth + 1)?;
            self.nodes += 1;
            if self.nodes > 1024 {
                return Err(invalid("custom expression syntax node budget exceeded"));
            }
            left = Expr::Binary(op, Box::new(left), Box::new(right));
        }
        Ok(left)
    }

    fn close(&mut self) -> GalResult<()> {
        if !matches!(self.tokens[self.cursor], Token::Close) {
            return Err(invalid("custom expression requires closing parenthesis"));
        }
        self.cursor += 1;
        Ok(())
    }
}
