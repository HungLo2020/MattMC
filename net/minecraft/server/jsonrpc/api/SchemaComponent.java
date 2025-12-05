package net.minecraft.server.jsonrpc.api;

import java.net.URI;

public record SchemaComponent(String name, URI ref, Schema schema) {
	public Schema asRef() {
		return Schema.ofRef(this.ref);
	}

	public Schema asArray() {
		return Schema.arrayOf(Schema.ofRef(this.ref));
	}
}
