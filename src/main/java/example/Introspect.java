/*
 * Copyright (c) 2023 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example;

import java.io.IOException;
import java.io.Serial;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Resource;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.github.f4b6a3.tsid.TsidFactory;

/**
 * UDF for creating a Graph database schema according to the format defined here:
 *
 * <a href="https://github.com/neo4j/graph-schema-json-js-utils">graph-schema-json-js-utils</a>, see scheme
 * <a href="https://github.com/neo4j/graph-schema-json-js-utils/blob/main/json-schema.json">here</a> and an example
 * <a href="https://github.com/neo4j/graph-schema-json-js-utils/blob/main/test/validation/test-schemas/full.json">here</a>.
 * <p>
 * The instrospector creates JSON ids based on the labels and types by default. It can alternatively use
 * Time-Sorted Unique Identifiers (TSID) for the ids inside the generated schema by calling it via
 * {@code RETURN db.introspect({constantIds:false}}
 */
public class Introspect {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
		.enable(SerializationFeature.INDENT_OUTPUT);
	private static final TsidFactory TSID_FACTORY = TsidFactory.builder()
		.withRandomFunction(length -> {
			final byte[] bytes = new byte[length];
			ThreadLocalRandom.current().nextBytes(bytes);
			return bytes;
		}).build();
	public static final String GRAPH_SCHEMA_REPRESENTATION_VERSION = "1.0.1";

	public static final Map<String, String> TYPE_MAPPING = Map.of(
		"Long", "integer",
		"Double", "float"
	);

	@Context
	public Transaction transaction;

	@UserFunction(name = "db.introspect")
	public String introspectSchema(@Name("params") Map<String, Object> params) throws Exception {

		var useConstantIds = (boolean) params.getOrDefault("constantIds", true);

		var nodeLabels = getNodeLabels(useConstantIds);
		var relationshipTypes = getRelationshipTypes(useConstantIds);
		var nodeObjectTypes = getNodeObjectTypes(useConstantIds, nodeLabels);

		try (var result = new StringWriter()) {
			try (var gen = OBJECT_MAPPER.createGenerator(result)) {
				gen.writeStartObject();
				gen.writeObjectFieldStart("graphSchemaRepresentation");
				gen.writeStringField("version", GRAPH_SCHEMA_REPRESENTATION_VERSION);
				gen.writeFieldName("graphSchema");
				gen.writeStartObject();
				writeArray(gen, "nodeLabels", nodeLabels.values());
				writeArray(gen, "relationshipTypes", relationshipTypes.values());
				writeArray(gen, "nodeObjectTypes", nodeObjectTypes.values());
				gen.writeEndObject();
				gen.writeEndObject();
				gen.writeEndObject();
			}
			result.flush();
			return result.toString();
		}
	}

	private Map<String, NodeObjectType> getNodeObjectTypes(boolean useConstantIds, Map<String, Token> labelIdToToken) throws Exception {

		if (labelIdToToken.isEmpty()) {
			return Map.of();
		}

		// language=cypher
		var query = """
			CALL db.schema.nodeTypeProperties()
			YIELD nodeType, nodeLabels, propertyName, propertyTypes, mandatory
			RETURN *
			""";

		BiFunction<String, List<String>, String> idGenerator;
		if (useConstantIds) {
			idGenerator = (id, nodeLabels) -> {
				var result = id;
				for (String nodeLabel : nodeLabels) {
					result = result.replaceAll(Pattern.quote("`%s`".formatted(nodeLabel)), nodeLabel);
				}
				return "n" + result;
			};
		} else {
			idGenerator = (id, nodeLabels) -> TSID_FACTORY.create().format("%S");
		}

		var nodeObjectTypes = new HashMap<String, NodeObjectType>();
		transaction.execute(query).accept((Result.ResultVisitor<Exception>) resultRow -> {
			@SuppressWarnings("unchecked")
			var nodeLabels = ((List<String>) resultRow.get("nodeLabels"));

			var id = idGenerator.apply(resultRow.getString("nodeType"), nodeLabels);
			var nodeObject = nodeObjectTypes.computeIfAbsent(id, key -> new NodeObjectType(key, nodeLabels
				.stream().map(l -> Map.of("ref", labelIdToToken.get(l).id)).toList()));
			@SuppressWarnings("unchecked")
			var types = ((List<String>) resultRow.get("propertyTypes")).stream()
				.map(t -> {
					String type;
					String itemType = null;
					if (t.endsWith("Array")) {
						type = "array";
						itemType = t.replace("Array", "");
						itemType = TYPE_MAPPING.getOrDefault(itemType, itemType).toLowerCase(Locale.ROOT);
					} else {
						type = TYPE_MAPPING.getOrDefault(t, t).toLowerCase(Locale.ROOT);
					}
					return new Type(type, itemType);
				}).toList();
			nodeObject.properties.add(new Property(resultRow.getString("propertyName"), types, resultRow.getBoolean("mandatory")));
			return true;
		});
		return nodeObjectTypes;
	}

	@JsonSerialize(using = TypeSerializer.class)
	record Type(String value, String itemType) {
	}

	@JsonPropertyOrder({"token", "type", "mandatory"})
	record Property(
		String token,
		@JsonProperty("type") @JsonSerialize(using = TypeListSerializer.class) List<Type> types,
		@JsonInclude(Include.NON_DEFAULT) boolean mandatory) {
	}

	record NodeObjectType(@JsonProperty("$id") String id, List<Map<String, String>> labels, List<Property> properties) {

		NodeObjectType(String id, List<Map<String, String>> labels) {
			this(id, labels, new ArrayList<>()); // Mutable on purpose
		}
	}


	private Map<String, Token> getNodeLabels(boolean useConstantIds) throws Exception {

		return getToken(transaction.getAllLabelsInUse(), Label::name, useConstantIds ? "nl:%s"::formatted : ignored -> TSID_FACTORY.create().format("%S"));
	}

	private Map<String, Token> getRelationshipTypes(boolean useConstantIds) throws Exception {

		return getToken(transaction.getAllRelationshipTypesInUse(), RelationshipType::name, useConstantIds ? "rt:%s"::formatted : ignored -> TSID_FACTORY.create().format("%S"));
	}

	private <T> Map<String, Token> getToken(Iterable<T> tokensInUse, Function<T, String> nameExtractor, UnaryOperator<String> idGenerator) throws Exception {

		try {
			return StreamSupport.stream(tokensInUse.spliterator(), false)
				.map(label -> {
					var tokenValue = nameExtractor.apply(label);
					return new Token(idGenerator.apply(tokenValue), tokenValue);
				})
				.collect(Collectors.toMap(Token::value, Function.identity()));
		} finally {
			if (tokensInUse instanceof Resource resource) {
				resource.close();
			}
		}
	}

	record Token(@JsonProperty("$id") String id, @JsonProperty("token") String value) {
	}

	private void writeArray(JsonGenerator gen, String fieldName, Collection<?> items) throws Exception {
		gen.writeArrayFieldStart(fieldName);
		for (Object item : items) {
			gen.writeObject(item);
		}
		gen.writeEndArray();
	}

	static class TypeSerializer extends StdSerializer<Type> {

		TypeSerializer() {
			super(Type.class);
		}

		@Override
		public void serialize(Type type, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {
			gen.writeStartObject();
			gen.writeStringField("type", type.value());
			if (type.value().equals("array")) {
				gen.writeObjectFieldStart("items");
				gen.writeStringField("type", type.itemType());
				gen.writeEndObject();
			}
			gen.writeEndObject();
		}
	}

	static class TypeListSerializer extends StdSerializer<List<Type>> {

		@Serial
		private static final long serialVersionUID = -8831424337461613203L;

		TypeListSerializer() {
			super(TypeFactory.defaultInstance().constructType(new TypeReference<List<Type>>() {
			}));
		}

		@Override
		public void serialize(List<Type> types, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {
			if (types.isEmpty()) {
				gen.writeNull();
			} else if (types.size() == 1) {
				gen.writeObject(types.get(0));
			} else {
				gen.writeObject(types);
			}
		}
	}
}

