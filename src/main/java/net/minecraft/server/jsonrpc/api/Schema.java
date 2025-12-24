package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.server.jsonrpc.methods.GameRulesService;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;

public record Schema(
	Optional<URI> reference, Optional<String> type, Optional<Schema> items, Optional<Map<String, Schema>> properties, Optional<List<String>> enumValues
) {
	public static final Codec<Schema> CODEC = Codec.recursive(
		"Schema",
		codec -> RecordCodecBuilder.create(
			instance -> instance.group(
					ReferenceUtil.REFERENCE_CODEC.optionalFieldOf("$ref").forGetter(Schema::reference),
					Codec.STRING.optionalFieldOf("type").forGetter(Schema::type),
					codec.optionalFieldOf("items").forGetter(Schema::items),
					Codec.unboundedMap(Codec.STRING, codec).optionalFieldOf("properties").forGetter(Schema::properties),
					Codec.STRING.listOf().optionalFieldOf("enum").forGetter(Schema::enumValues)
				)
				.apply(instance, Schema::new)
		)
	);
	private static final List<SchemaComponent> SCHEMA_REGISTRY = new ArrayList();
	public static final Schema BOOL_SCHEMA = ofType("boolean");
	public static final Schema INT_SCHEMA = ofType("integer");
	public static final Schema NUMBER_SCHEMA = ofType("number");
	public static final Schema STRING_SCHEMA = ofType("string");
	public static final Schema UUID_SCHEMA = STRING_SCHEMA;
	public static final SchemaComponent DIFFICULTY_SCHEMA = registerSchema("difficulty", ofEnum(Difficulty::values));
	public static final SchemaComponent GAME_TYPE_SCHEMA = registerSchema("game_type", ofEnum(GameType::values));
	public static final SchemaComponent PLAYER_SCHEMA = registerSchema("player", record().withField("id", UUID_SCHEMA).withField("name", STRING_SCHEMA));
	public static final SchemaComponent VERSION_SCHEMA = registerSchema("version", record().withField("name", STRING_SCHEMA).withField("protocol", INT_SCHEMA));
	public static final SchemaComponent SERVER_STATE_SCHEMA = registerSchema(
		"server_state", record().withField("started", BOOL_SCHEMA).withField("players", PLAYER_SCHEMA.asRef().asArray()).withField("version", VERSION_SCHEMA.asRef())
	);
	public static final Schema RULE_TYPE_SCHEMA = ofEnum(GameRulesService.RuleType::values);
	public static final SchemaComponent TYPED_GAME_RULE_SCHEMA = registerSchema(
		"typed_game_rule", record().withField("key", STRING_SCHEMA).withField("value", STRING_SCHEMA).withField("type", RULE_TYPE_SCHEMA)
	);
	public static final SchemaComponent UNTYPED_GAME_RULE_SCHEMA = registerSchema(
		"untyped_game_rule", record().withField("key", STRING_SCHEMA).withField("value", STRING_SCHEMA)
	);
	public static final SchemaComponent MESSAGE_SCHEMA = registerSchema(
		"message", record().withField("literal", STRING_SCHEMA).withField("translatable", STRING_SCHEMA).withField("translatableParams", STRING_SCHEMA.asArray())
	);
	public static final SchemaComponent SYSTEM_MESSAGE_SCHEMA = registerSchema(
		"system_message",
		record().withField("message", MESSAGE_SCHEMA.asRef()).withField("overlay", BOOL_SCHEMA).withField("receivingPlayers", PLAYER_SCHEMA.asRef().asArray())
	);
	public static final SchemaComponent KICK_PLAYER_SCHEMA = registerSchema(
		"kick_player", record().withField("message", MESSAGE_SCHEMA.asRef()).withField("player", PLAYER_SCHEMA.asRef())
	);
	public static final SchemaComponent OPERATOR_SCHEMA = registerSchema(
		"operator", record().withField("player", PLAYER_SCHEMA.asRef()).withField("bypassesPlayerLimit", BOOL_SCHEMA).withField("permissionLevel", INT_SCHEMA)
	);
	public static final SchemaComponent INCOMING_IP_BAN_SCHEMA = registerSchema(
		"incoming_ip_ban",
		record()
			.withField("player", PLAYER_SCHEMA.asRef())
			.withField("ip", STRING_SCHEMA)
			.withField("reason", STRING_SCHEMA)
			.withField("source", STRING_SCHEMA)
			.withField("expires", STRING_SCHEMA)
	);
	public static final SchemaComponent IP_BAN_SCHEMA = registerSchema(
		"ip_ban", record().withField("ip", STRING_SCHEMA).withField("reason", STRING_SCHEMA).withField("source", STRING_SCHEMA).withField("expires", STRING_SCHEMA)
	);
	public static final SchemaComponent PLAYER_BAN_SCHEMA = registerSchema(
		"user_ban",
		record().withField("player", PLAYER_SCHEMA.asRef()).withField("reason", STRING_SCHEMA).withField("source", STRING_SCHEMA).withField("expires", STRING_SCHEMA)
	);

	private static SchemaComponent registerSchema(String string, Schema schema) {
		SchemaComponent schemaComponent = new SchemaComponent(string, ReferenceUtil.createLocalReference(string), schema);
		SCHEMA_REGISTRY.add(schemaComponent);
		return schemaComponent;
	}

	public static List<SchemaComponent> getSchemaRegistry() {
		return SCHEMA_REGISTRY;
	}

	public static Schema ofRef(URI uRI) {
		return new Schema(Optional.of(uRI), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static Schema ofType(String string) {
		return new Schema(Optional.empty(), Optional.of(string), Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static <E extends Enum<E> & StringRepresentable> Schema ofEnum(Supplier<E[]> supplier) {
		List<String> list = Stream.of((Enum[])supplier.get()).map(object -> ((StringRepresentable)object).getSerializedName()).toList();
		return ofEnum(list);
	}

	public static Schema ofEnum(List<String> list) {
		return new Schema(Optional.empty(), Optional.of("string"), Optional.empty(), Optional.empty(), Optional.of(list));
	}

	public static Schema arrayOf(Schema schema) {
		return new Schema(Optional.empty(), Optional.of("array"), Optional.of(schema), Optional.empty(), Optional.empty());
	}

	public static Schema record() {
		return new Schema(Optional.empty(), Optional.of("object"), Optional.empty(), Optional.empty(), Optional.empty());
	}

	public static Schema record(Map<String, Schema> map) {
		return new Schema(Optional.empty(), Optional.of("object"), Optional.empty(), Optional.of(map), Optional.empty());
	}

	public Schema withField(String string, Schema schema) {
		HashMap<String, Schema> hashMap = new HashMap();
		this.properties.ifPresent(hashMap::putAll);
		hashMap.put(string, schema);
		return record(hashMap);
	}

	public Schema asArray() {
		return arrayOf(this);
	}
}
