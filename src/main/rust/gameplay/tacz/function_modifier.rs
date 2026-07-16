use std::slice;
use std::str;

const STATUS_OK: i32 = 0;
const STATUS_NULL_EXPRESSION: i32 = 1;
const STATUS_INVALID_LENGTH: i32 = 2;
const STATUS_INVALID_UTF8: i32 = 3;
const STATUS_NULL_OUTPUT: i32 = 4;
const STATUS_EVAL_ERROR: i32 = 5;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EvalError {
    InvalidExpression,
}

pub fn eval(value: f64, input: f64, function: &str) -> Result<f64, EvalError> {
    eval_unchecked(value, input, function)
}

pub fn eval_or_current(value: f64, input: f64, function: &str) -> f64 {
    eval(value, input, function).unwrap_or(value)
}

fn eval_unchecked(value: f64, input: f64, function: &str) -> Result<f64, EvalError> {
    let script = function.trim().to_lowercase();
    if script.starts_with("if") {
        let Some(then_index) = script.find("then") else {
            return Ok(value);
        };
        let Some(else_offset) = script[then_index + 4..].find("else") else {
            return Ok(value);
        };
        let else_index = then_index + 4 + else_offset;
        let Some(end_index) = script.rfind("end") else {
            return Ok(value);
        };

        let mut condition = script[2..then_index].trim();
        if condition.starts_with('(') && condition.ends_with(')') {
            condition = &condition[1..condition.len() - 1];
        }

        let branch = if eval_condition(condition, value, input)? {
            &script[then_index + 4..else_index]
        } else {
            &script[else_index + 4..end_index]
        };
        return eval_assignment(branch, value, input);
    }

    eval_assignment(&script, value, input)
}

fn eval_condition(condition: &str, value: f64, input: f64) -> Result<bool, EvalError> {
    for operator in [">=", "<=", "==", "~=", ">", "<"] {
        if let Some(index) = condition.find(operator) {
            let left = ExpressionParser::new(&condition[..index], value, input).parse()?;
            let right = ExpressionParser::new(&condition[index + operator.len()..], value, input)
                .parse()?;
            return Ok(match operator {
                ">=" => left >= right,
                "<=" => left <= right,
                "==" => left == right,
                "~=" => left != right,
                ">" => left > right,
                "<" => left < right,
                _ => false,
            });
        }
    }

    Ok(ExpressionParser::new(condition, value, input).parse()? != 0.0)
}

fn eval_assignment(assignment: &str, value: f64, input: f64) -> Result<f64, EvalError> {
    let mut trimmed = assignment.trim();
    if let Some(equals) = trimmed.find('=') {
        let target = trimmed[..equals].trim();
        if target != "y" {
            return Ok(value);
        }
        trimmed = &trimmed[equals + 1..];
    }

    ExpressionParser::new(trimmed, value, input).parse()
}

struct ExpressionParser<'a> {
    expression: &'a str,
    x: f64,
    r: f64,
    cursor: usize,
}

impl<'a> ExpressionParser<'a> {
    fn new(expression: &'a str, x: f64, r: f64) -> Self {
        Self {
            expression,
            x,
            r,
            cursor: 0,
        }
    }

    fn parse(mut self) -> Result<f64, EvalError> {
        let result = self.parse_add_subtract()?;
        self.skip_whitespace();
        if self.cursor != self.expression.len() {
            return Err(EvalError::InvalidExpression);
        }
        Ok(result)
    }

    fn parse_add_subtract(&mut self) -> Result<f64, EvalError> {
        let mut value = self.parse_multiply_divide()?;
        loop {
            self.skip_whitespace();
            if self.consume('+') {
                value += self.parse_multiply_divide()?;
            } else if self.consume('-') {
                value -= self.parse_multiply_divide()?;
            } else {
                return Ok(value);
            }
        }
    }

    fn parse_multiply_divide(&mut self) -> Result<f64, EvalError> {
        let mut value = self.parse_unary()?;
        loop {
            self.skip_whitespace();
            if self.consume('*') {
                value *= self.parse_unary()?;
            } else if self.consume('/') {
                value /= self.parse_unary()?;
            } else {
                return Ok(value);
            }
        }
    }

    fn parse_unary(&mut self) -> Result<f64, EvalError> {
        self.skip_whitespace();
        if self.consume('+') {
            return self.parse_unary();
        }
        if self.consume('-') {
            return Ok(-self.parse_unary()?);
        }
        self.parse_primary()
    }

    fn parse_primary(&mut self) -> Result<f64, EvalError> {
        self.skip_whitespace();
        if self.consume('(') {
            let value = self.parse_add_subtract()?;
            if !self.consume(')') {
                return Err(EvalError::InvalidExpression);
            }
            return Ok(value);
        }
        if self.consume('x') {
            return Ok(self.x);
        }
        if self.consume('r') {
            return Ok(self.r);
        }

        let start = self.cursor;
        while self.cursor < self.expression.len() {
            let byte = self.expression.as_bytes()[self.cursor];
            if byte.is_ascii_digit() || byte == b'.' {
                self.cursor += 1;
            } else {
                break;
            }
        }
        if start == self.cursor {
            return Err(EvalError::InvalidExpression);
        }

        self.expression[start..self.cursor]
            .parse::<f64>()
            .map_err(|_| EvalError::InvalidExpression)
    }

    fn consume(&mut self, expected: char) -> bool {
        self.skip_whitespace();
        if self.expression[self.cursor..].starts_with(expected) {
            self.cursor += expected.len_utf8();
            true
        } else {
            false
        }
    }

    fn skip_whitespace(&mut self) {
        while self.cursor < self.expression.len() {
            let c = self.expression[self.cursor..]
                .chars()
                .next()
                .expect("cursor is within expression");
            if !c.is_whitespace() {
                break;
            }
            self.cursor += c.len_utf8();
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_tacz_function_modifier_eval(
    expression_ptr: *const u8,
    expression_len: u64,
    value: f64,
    input: f64,
    output: *mut f64,
) -> i32 {
    if output.is_null() {
        return STATUS_NULL_OUTPUT;
    }
    if expression_ptr.is_null() {
        return STATUS_NULL_EXPRESSION;
    }
    if expression_len > isize::MAX as u64 {
        return STATUS_INVALID_LENGTH;
    }

    let bytes = unsafe { slice::from_raw_parts(expression_ptr, expression_len as usize) };
    let Ok(function) = str::from_utf8(bytes) else {
        return STATUS_INVALID_UTF8;
    };

    match eval(value, input, function) {
        Ok(result) => {
            unsafe {
                *output = result;
            }
            STATUS_OK
        }
        Err(_) => STATUS_EVAL_ERROR,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn assert_close(expected: f64, actual: f64) {
        assert!(
            (expected - actual).abs() < 0.0000001,
            "expected {expected}, got {actual}"
        );
    }

    #[test]
    fn evaluates_current_java_tacz_snippets() {
        assert_close(
            1.2,
            eval(0.8, 0.8, "if (x > 0.5) then y = x*1.5 else y = x*1.75 end").unwrap(),
        );
        assert_close(
            0.7,
            eval(0.4, 0.4, "if (x > 0.5) then y = x*1.5 else y = x*1.75 end").unwrap(),
        );
        assert_close(
            17.0,
            eval(22.0, 12.0, "if (x > 20) then y = r + 5 else y = x * 3 end").unwrap(),
        );
        assert_close(
            12.0,
            eval(4.0, 4.0, "if (x > 20) then y = r + 5 else y = x * 3 end").unwrap(),
        );
    }

    #[test]
    fn supports_arithmetic_precedence_unary_parentheses_and_whitespace() {
        assert_close(7.0, eval(2.0, 3.0, " y = 1 + x * r ").unwrap());
        assert_close(9.0, eval(2.0, 3.0, "y = (1 + x) * r").unwrap());
        assert_close(-5.0, eval(2.0, 3.0, "y = -x - r").unwrap());
        assert_close(5.0, eval(2.0, 3.0, "y = +x + +r").unwrap());
        assert_close(2.5, eval(2.0, 3.0, "y = 10 / (r + 1)").unwrap());
    }

    #[test]
    fn supports_comparison_operators_and_truthy_expression_conditions() {
        assert_close(
            1.0,
            eval(2.0, 2.0, "if x >= r then y = 1 else y = 0 end").unwrap(),
        );
        assert_close(
            1.0,
            eval(2.0, 3.0, "if x <= r then y = 1 else y = 0 end").unwrap(),
        );
        assert_close(
            1.0,
            eval(2.0, 2.0, "if x == r then y = 1 else y = 0 end").unwrap(),
        );
        assert_close(
            1.0,
            eval(2.0, 3.0, "if x ~= r then y = 1 else y = 0 end").unwrap(),
        );
        assert_close(
            1.0,
            eval(3.0, 2.0, "if x > r then y = 1 else y = 0 end").unwrap(),
        );
        assert_close(
            1.0,
            eval(2.0, 3.0, "if x < r then y = 1 else y = 0 end").unwrap(),
        );
        assert_close(
            9.0,
            eval(0.0, 3.0, "if r - 3 then y = 1 else y = 9 end").unwrap(),
        );
    }

    #[test]
    fn preserves_java_assignment_and_failure_fallback_rules() {
        assert_close(5.0, eval(5.0, 2.0, "z = x + 1").unwrap());
        assert_close(5.0, eval(5.0, 2.0, "if x then y = 1").unwrap());
        assert_close(5.0, eval_or_current(5.0, 2.0, "y = "));
        assert_close(5.0, eval_or_current(5.0, 2.0, "y = x + nope"));
        assert!(eval(5.0, 2.0, "y = ").is_err());
    }

    #[test]
    fn preserves_division_and_floating_point_edge_behavior() {
        assert!(eval(1.0, 0.0, "y = x / r").unwrap().is_infinite());
        assert!(eval(0.0, 0.0, "y = x / r").unwrap().is_nan());
        assert!(eval(1.0, 1.0, "y = 1e3").is_err());
        assert_close(0.5, eval(1.0, 1.0, "y = .5").unwrap());
    }

    #[test]
    fn ffi_accepts_valid_utf8_and_rejects_malformed_inputs() {
        let expression = b"y = x + r";
        let mut output = 0.0;

        let status = unsafe {
            mattmc_tacz_function_modifier_eval(
                expression.as_ptr(),
                expression.len() as u64,
                2.0,
                3.0,
                &mut output,
            )
        };
        assert_eq!(STATUS_OK, status);
        assert_close(5.0, output);

        assert_eq!(STATUS_NULL_EXPRESSION, unsafe {
            mattmc_tacz_function_modifier_eval(std::ptr::null(), 0, 2.0, 3.0, &mut output)
        });
        assert_eq!(STATUS_NULL_OUTPUT, unsafe {
            mattmc_tacz_function_modifier_eval(
                expression.as_ptr(),
                expression.len() as u64,
                2.0,
                3.0,
                std::ptr::null_mut(),
            )
        });
        assert_eq!(STATUS_INVALID_LENGTH, unsafe {
            mattmc_tacz_function_modifier_eval(expression.as_ptr(), u64::MAX, 2.0, 3.0, &mut output)
        });
        assert_eq!(STATUS_INVALID_UTF8, unsafe {
            mattmc_tacz_function_modifier_eval([0xFF].as_ptr(), 1, 2.0, 3.0, &mut output)
        });
        assert_eq!(STATUS_EVAL_ERROR, unsafe {
            mattmc_tacz_function_modifier_eval(b"y =".as_ptr(), 3, 2.0, 3.0, &mut output)
        });
    }
}
