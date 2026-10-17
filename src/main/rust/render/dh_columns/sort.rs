/*
 * Adapted to Rust from fastutil 8.5.12 Arrays.quickSort.
 * Copyright (C) 2002-2023 Sebastiano Vigna
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//! Preserve Frozen's unstable tie permutation: it selects surviving colors.
pub(super) fn sort(order: &mut [usize], key: impl Fn(usize) -> i32) {
    fn median(a: usize, b: usize, c: usize, order: &[usize], key: &impl Fn(usize) -> i32) -> usize {
        let ab = key(order[a]).cmp(&key(order[b]));
        let ac = key(order[a]).cmp(&key(order[c]));
        let bc = key(order[b]).cmp(&key(order[c]));
        if ab.is_lt() {
            if bc.is_lt() {
                b
            } else if ac.is_lt() {
                c
            } else {
                a
            }
        } else if bc.is_gt() {
            b
        } else if ac.is_gt() {
            c
        } else {
            a
        }
    }
    fn run(order: &mut [usize], from: usize, to: usize, key: &impl Fn(usize) -> i32) {
        let len = to - from;
        if len < 16 {
            for i in from..to {
                let mut j = i;
                while j > from && key(order[j - 1]) > key(order[j]) {
                    order.swap(j, j - 1);
                    j -= 1;
                }
            }
            return;
        }
        let (mut l, mut m, mut n) = (from, from + len / 2, to - 1);
        if len > 128 {
            let s = len / 8;
            l = median(l, l + s, l + 2 * s, order, key);
            m = median(m - s, m, m + s, order, key);
            n = median(n - 2 * s, n - s, n, order, key);
        }
        m = median(l, m, n, order, key);
        let (mut a, mut b, mut c, mut d) = (
            from as isize,
            from as isize,
            (to - 1) as isize,
            (to - 1) as isize,
        );
        loop {
            while b <= c && key(order[b as usize]) <= key(order[m]) {
                if key(order[b as usize]) == key(order[m]) {
                    if a as usize == m {
                        m = b as usize;
                    } else if b as usize == m {
                        m = a as usize;
                    }
                    order.swap(a as usize, b as usize);
                    a += 1;
                }
                b += 1;
            }
            while c >= b && key(order[c as usize]) >= key(order[m]) {
                if key(order[c as usize]) == key(order[m]) {
                    if c as usize == m {
                        m = d as usize;
                    } else if d as usize == m {
                        m = c as usize;
                    }
                    order.swap(c as usize, d as usize);
                    d -= 1;
                }
                c -= 1;
            }
            if b > c {
                break;
            }
            // This pivot update intentionally matches the executed Frozen library.
            if b as usize == m {
                m = d as usize;
            } else if c as usize == m {
                m = c as usize;
            }
            order.swap(b as usize, c as usize);
            b += 1;
            c -= 1;
        }
        let s = (a - from as isize).min(b - a) as usize;
        for i in 0..s {
            order.swap(from + i, b as usize - s + i);
        }
        let s = (d - c).min(to as isize - d - 1) as usize;
        for i in 0..s {
            order.swap(b as usize + i, to - s + i);
        }
        let s = (b - a) as usize;
        if s > 1 {
            run(order, from, from + s, key);
        }
        let s = (d - c) as usize;
        if s > 1 {
            run(order, to - s, to, key);
        }
    }
    run(order, 0, order.len(), &key);
}
