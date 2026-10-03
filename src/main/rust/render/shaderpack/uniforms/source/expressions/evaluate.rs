//! One bounded evaluation of the immutable expression DAG.

use super::*;

pub(super) fn node(
    program: &Program,
    id: Id,
    frame: &TerrainSourceUniformFrame,
    memo: &mut [Option<Value>],
) -> GalResult<Value> {
    if let Some(value) = memo[id as usize] {
        return Ok(value);
    }
    let expression = &program.nodes[id as usize];
    let value = match &expression.kind {
        Kind::Literal(value) => match value {
            Literal::Float(bits) => Value::Float(f32::from_bits(*bits)),
            Literal::Int(value) => Value::Int(*value),
        },
        Kind::Input(input) => input.read(frame)?,
        Kind::Neg(child) => match node(program, *child, frame, memo)? {
            Value::Int(value) => Value::Int(value.wrapping_neg()),
            Value::Float(value) => Value::Float(-value),
            _ => unreachable!("typed negation"),
        },
        Kind::Not(child) => Value::Bool(!node(program, *child, frame, memo)?.boolean()?),
        Kind::Cast(child) => match (expression.ty, node(program, *child, frame, memo)?) {
            (Ty::Float, Value::Int(value)) => Value::Float(value as f32),
            (Ty::Int, Value::Float(value)) => Value::Int(value as i32),
            _ => unreachable!("typed numeric cast"),
        },
        Kind::Component(child, component) => match node(program, *child, frame, memo)? {
            Value::Vector(values, _) => Value::Float(values[*component as usize]),
            _ => unreachable!("typed vector component"),
        },
        Kind::Vector(children) => {
            let mut values = [0.0; 4];
            for (index, &child) in children.iter().enumerate() {
                values[index] = node(program, child, frame, memo)?.float()?;
            }
            Value::Vector(values, children.len() as u8)
        }
        Kind::Select(branches, otherwise) => {
            let mut selected = *otherwise;
            for &(condition, value) in branches {
                if node(program, condition, frame, memo)?.boolean()? {
                    selected = value;
                    break;
                }
            }
            node(program, selected, frame, memo)?
        }
        Kind::Binary(op, left, right) => {
            let a = node(program, *left, frame, memo)?;
            // Conditional values are lazy, as Iris's `if` is; ordinary boolean
            // operators evaluate their typed operands through the DAG.
            let b = node(program, *right, frame, memo)?;
            binary(*op, a, b)?
        }
        Kind::Function(function, children) => {
            let mut values = [Value::Int(0); 65];
            for (index, &child) in children.iter().enumerate() {
                values[index] = node(program, child, frame, memo)?;
            }
            call(*function, &values[..children.len()], expression.ty)?
        }
    }
    .finite()?;
    memo[id as usize] = Some(value);
    Ok(value)
}

fn binary(op: Op, a: Value, b: Value) -> GalResult<Value> {
    use Op::*;
    Ok(match (a, b) {
        (Value::Bool(a), Value::Bool(b)) => Value::Bool(match op {
            And => a && b,
            Or => a || b,
            Eq => a == b,
            Ne => a != b,
            _ => unreachable!("typed boolean operator"),
        }),
        (Value::Int(a), Value::Int(b)) => match op {
            Add => Value::Int(a.wrapping_add(b)),
            Sub => Value::Int(a.wrapping_sub(b)),
            Mul => Value::Int(a.wrapping_mul(b)),
            Rem => Value::Int(remainder(a, b)?),
            Eq => Value::Bool(a == b),
            Ne => Value::Bool(a != b),
            Lt => Value::Bool(a < b),
            Le => Value::Bool(a <= b),
            Gt => Value::Bool(a > b),
            Ge => Value::Bool(a >= b),
            _ => unreachable!("typed integer operator"),
        },
        (Value::Float(a), Value::Float(b)) => match op {
            Add => Value::Float(a + b),
            Sub => Value::Float(a - b),
            Mul => Value::Float(a * b),
            Div => Value::Float(a / b),
            Rem => Value::Float(a % b),
            Eq => Value::Bool(a == b),
            Ne => Value::Bool(a != b),
            Lt => Value::Bool(a < b),
            Le => Value::Bool(a <= b),
            Gt => Value::Bool(a > b),
            Ge => Value::Bool(a >= b),
            _ => unreachable!("typed float operator"),
        },
        _ => unreachable!("typed binary operands"),
    })
}

fn remainder(a: i32, b: i32) -> GalResult<i32> {
    if b == 0 {
        return Err(invalid("custom integer remainder divides by zero"));
    }
    Ok(a.checked_rem(b).unwrap_or(0)) // Java MIN_VALUE % -1 is zero.
}

fn call(function: Function, values: &[Value], ty: Ty) -> GalResult<Value> {
    use Function::*;
    if ty == Ty::Int {
        if matches!(function, Floor | Ceil) {
            let value = values[0].float()?;
            return Ok(Value::Int(if function == Floor {
                value.floor() as i32
            } else {
                value.ceil() as i32
            }));
        }
        let mut integers = [0; 65];
        for (index, value) in values.iter().enumerate() {
            integers[index] = match value {
                Value::Int(value) => *value,
                _ => unreachable!("typed integer function"),
            };
        }
        let values = &integers[..values.len()];
        let a = values[0];
        return Ok(Value::Int(match function {
            Min => *values.iter().min().unwrap(),
            Max => *values.iter().max().unwrap(),
            Clamp => a.min(values[2]).max(values[1]),
            Abs => a.wrapping_abs(),
            Fmod => {
                let b = values[1];
                let remainder = remainder(a, b)?;
                if remainder != 0 && (a ^ b) < 0 {
                    remainder.wrapping_add(b)
                } else {
                    remainder
                }
            }
            _ => unreachable!("typed integer function"),
        }));
    }
    let mut floats = [0.0; 65];
    for (index, value) in values.iter().enumerate() {
        floats[index] = value.float()?;
    }
    let values = &floats[..values.len()];
    let a = values[0];
    Ok(Value::Float(match function {
        Min => values.iter().copied().fold(f32::INFINITY, f32::min),
        Max => values.iter().copied().fold(f32::NEG_INFINITY, f32::max),
        Clamp => a.min(values[2]).max(values[1]),
        Fmod => (a % values[1] + values[1]) % values[1],
        Atan => {
            if values.len() == 1 {
                (a as f64).atan() as f32
            } else {
                (a as f64).atan2(values[1] as f64) as f32
            }
        }
        Log => {
            if values.len() == 1 {
                (a as f64).ln() as f32
            } else {
                ((values[1] as f64).ln() / (a as f64).ln()) as f32
            }
        }
        Sqrt => (a as f64).sqrt() as f32,
        Pow => (a as f64).powf(values[1] as f64) as f32,
        Sin => (a as f64).sin() as f32,
        Cos => (a as f64).cos() as f32,
        Exp => (a as f64).exp() as f32,
        Abs => a.abs(),
        Floor => a.floor(),
        Ceil => a.ceil(),
        Frac => a - a.floor(),
    }))
}
