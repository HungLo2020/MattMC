# bridge

The Java boundary: the C ABI, record decoding and context lifetime. It copies
what Java sends and calls the world and GUI renderers; it makes no rendering
decisions itself. It is the one place that chooses which GAL backend to create.

Will receive: `vulkanic/ffi/`.
