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
package org.neo4j.graph_schema.introspector;

import java.io.IOException;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.neo4j.graph_schema.introspector.GraphSchema.NodeObjectType;
import org.neo4j.graph_schema.introspector.GraphSchema.Property;
import org.neo4j.graph_schema.introspector.GraphSchema.Ref;
import org.neo4j.graph_schema.introspector.GraphSchema.RelationshipObjectType;
import org.neo4j.graph_schema.introspector.GraphSchema.Token;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Encapsulating all the Jackson-JSON stuff.
 */
final class GraphSchemaModule extends SimpleModule {

	@Serial
	private static final long serialVersionUID = -4886300307467434436L;

	private static volatile ObjectMapper OBJECT_MAPPER;

	static ObjectMapper getGraphSchemaObjectMapper() {
		var result = OBJECT_MAPPER;
		if (result == null) {
			synchronized (GraphSchemaModule.class) {
				result = OBJECT_MAPPER;
				if (result == null) {
					OBJECT_MAPPER = new ObjectMapper();
					OBJECT_MAPPER.registerModule(new GraphSchemaModule());
					result = OBJECT_MAPPER;
				}
			}

		}
		return result;
	}

	// The nested maps render quite useless in browser
	static String asJsonString(List<Property> properties) {
		try {
			return getGraphSchemaObjectMapper().writeValueAsString(properties);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	GraphSchemaModule() {

		addSerializer(GraphSchema.class, new GraphSchemaSerializer());
		addDeserializer(GraphSchema.class, new GraphSchemaDeserializer());

		addSerializer(GraphSchema.Type.class, new TypeSerializer());
		addDeserializer(GraphSchema.Type.class, new TypeDeserializer());

		addSerializer(Ref.class, new RefSerializer());
		addDeserializer(Ref.class, new RefDeserializer());

		setMixInAnnotation(Property.class, PropertyMixin.class);
		setMixInAnnotation(NodeObjectType.class, NodeObjectTypeMixin.class);
		setMixInAnnotation(Token.class, TokenMixin.class);
		setMixInAnnotation(RelationshipObjectType.class, RelationshipObjectTypeMixin.class);
	}

	private static final class GraphSchemaDeserializer extends StdDeserializer<GraphSchema> {

		@Serial
		private static final long serialVersionUID = 1997954349856031416L;

		GraphSchemaDeserializer() {
			super(GraphSchema.class);
		}

		@Override
		public GraphSchema deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {

			var nodeLabels = Map.<String, Token>of();
			var relationshipTypes = Map.<String, Token>of();
			var nodeObjectTypes = Map.<Ref, NodeObjectType>of();
			var relationshipObjectTypes = Map.<Ref, RelationshipObjectType>of();

			var listOfTokens = new TypeReference<List<Token>>() {
			};
			var listOfNodeObjectTypes = new TypeReference<List<NodeObjectType>>() {
			};
			var listOfRelationshipObjectType = new TypeReference<List<RelationshipObjectType>>() {
			};

			jsonParser.nextFieldName();
			while (jsonParser.currentName() != null) {
				jsonParser.nextValue();
				switch (jsonParser.currentName()) {
					case "nodeLabels":
						nodeLabels = jsonParser.<List<Token>>readValueAs(listOfTokens).stream().collect(Collectors.toUnmodifiableMap(Token::id, Function.identity()));
						break;
					case "relationshipTypes":
						relationshipTypes = jsonParser.<List<Token>>readValueAs(listOfTokens).stream().collect(Collectors.toUnmodifiableMap(Token::id, Function.identity()));
						break;
					case "nodeObjectTypes":
						nodeObjectTypes = jsonParser.<List<NodeObjectType>>readValueAs(listOfNodeObjectTypes).stream().collect(Collectors.toUnmodifiableMap(ot -> new Ref(ot.id()), Function.identity()));
						break;
					case "relationshipObjectTypes":
						relationshipObjectTypes = jsonParser.<List<RelationshipObjectType>>readValueAs(listOfRelationshipObjectType).stream().collect(Collectors.toUnmodifiableMap(ot -> new Ref(ot.id()), Function.identity()));
						break;
				}
				jsonParser.nextFieldName();
			}
			return new GraphSchema(nodeLabels, relationshipTypes, nodeObjectTypes, relationshipObjectTypes);
		}
	}

	private static final class GraphSchemaSerializer extends StdSerializer<GraphSchema> {

		@Serial
		private static final long serialVersionUID = 3421593591346480162L;

		GraphSchemaSerializer() {
			super(GraphSchema.class);
		}

		@Override
		public void serialize(GraphSchema value, JsonGenerator gen, SerializerProvider provider) throws IOException {

			gen.writeStartObject();
			gen.writeObjectFieldStart("graphSchemaRepresentation");
			gen.writeFieldName("graphSchema");
			gen.writeStartObject();
			writeArray(gen, "nodeLabels", value.nodeLabels().values());
			writeArray(gen, "relationshipTypes", value.relationshipTypes().values());
			writeArray(gen, "nodeObjectTypes", value.nodeObjectTypes().values());
			writeArray(gen, "relationshipObjectTypes", value.relationshipObjectTypes().values());
			gen.writeEndObject();
			gen.writeEndObject();
			gen.writeEndObject();
		}

		private void writeArray(JsonGenerator gen, String fieldName, Collection<?> items) throws IOException {
			gen.writeArrayFieldStart(fieldName);
			for (Object item : items) {
				gen.writeObject(item);
			}
			gen.writeEndArray();
		}
	}

	private static final class TypeDeserializer extends StdDeserializer<GraphSchema.Type> {

		@Serial
		private static final long serialVersionUID = -1260953273076427362L;

		TypeDeserializer() {
			super(GraphSchema.Type.class);
		}

		@Override
		public GraphSchema.Type deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {

			jsonParser.nextValue();
			var type = jsonParser.getValueAsString();
			jsonParser.nextToken();
			return new GraphSchema.Type(type, null);
		}
	}

	private static final class TypeSerializer extends StdSerializer<GraphSchema.Type> {

		@Serial
		private static final long serialVersionUID = -1260953273076427362L;

		TypeSerializer() {
			super(GraphSchema.Type.class);
		}

		@Override
		public void serialize(GraphSchema.Type type, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {
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

	@JsonPropertyOrder({"token", "type", "nullable"})
	@JsonIgnoreProperties("$id")
	private abstract static class PropertyMixin {

		@JsonCreator
		PropertyMixin(String token,
			@JsonProperty("type")
			@JsonDeserialize(using = TypeListDeserializer.class)
			List<GraphSchema.Type> types,
			@JsonProperty("nullable")
			@JsonDeserialize(using = InvertingBooleanDeserializer.class)
			boolean mandatory
		) {
		}

		@JsonProperty("type")
		@JsonSerialize(using = TypeListSerializer.class)
		abstract List<GraphSchema.Type> types();

		@JsonProperty("nullable")
		@JsonSerialize(using = InvertingBooleanSerializer.class)
		abstract boolean mandatory();

	}

	private static class InvertingBooleanDeserializer extends StdDeserializer<Boolean> {

		@Serial
		private static final long serialVersionUID = 6272997898442893145L;

		InvertingBooleanDeserializer() {
			super(Boolean.class);
		}

		@Override
		public Boolean deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
			return !jsonParser.getBooleanValue();
		}
	}

	private static class InvertingBooleanSerializer extends StdSerializer<Boolean> {

		@Serial
		private static final long serialVersionUID = 6272997898442893145L;

		InvertingBooleanSerializer() {
			super(Boolean.class);
		}

		@Override
		public void serialize(Boolean value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeBoolean(!Boolean.TRUE.equals(value));
		}
	}

	@JsonPropertyOrder({"labels", "properties", "$id"})
	private abstract static class NodeObjectTypeMixin {

		@JsonCreator
		NodeObjectTypeMixin(@JsonProperty("$id") String $id, List<Ref> labels, List<Property> properties) {
		}

		@JsonProperty("$id")
		abstract String id();
	}

	private abstract static class TokenMixin {

		@JsonCreator
		TokenMixin(
			@JsonProperty("$id") String id,
			@JsonProperty("token") String value) {
		}

		@JsonProperty("$id")
		abstract String id();

		@JsonProperty("token")
		abstract String value();
	}

	private static final class RefDeserializer extends StdDeserializer<Ref> {

		@Serial
		private static final long serialVersionUID = -453459270591351643L;

		RefDeserializer() {
			super(Ref.class);
		}

		@Override
		public Ref deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {

			jsonParser.nextValue();
			var ref = new Ref(jsonParser.getValueAsString().substring(1));
			jsonParser.nextToken();
			return ref;
		}
	}

	private static final class RefSerializer extends StdSerializer<Ref> {

		@Serial
		private static final long serialVersionUID = -3928051476420574836L;

		RefSerializer() {
			super(Ref.class);
		}

		@Override
		public void serialize(Ref value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeStartObject();
			gen.writeObjectField("$ref", "#" + value.value());
			gen.writeEndObject();
		}
	}

	@JsonPropertyOrder({"type", "from", "to", "properties", "$id"})
	private abstract static class RelationshipObjectTypeMixin {

		@JsonCreator
		RelationshipObjectTypeMixin(@JsonProperty("$id") String $id, Ref type, Ref from, Ref to, List<Property> properties) {

		}

		@JsonProperty("$id")
		abstract String id();
	}

	private static final class TypeListDeserializer extends StdDeserializer<List<GraphSchema.Type>> {

		@Serial
		private static final long serialVersionUID = -8831424337461613203L;

		private static final TypeReference<List<GraphSchema.Type>> listOfTypes = new TypeReference<>() {
		};

		TypeListDeserializer() {
			super(TypeFactory.defaultInstance().constructType(listOfTypes));
		}

		@Override
		public List<GraphSchema.Type> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
			if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
				jsonParser.nextToken();
				return List.of(jsonParser.readValueAs(GraphSchema.Type.class));
			}
			return jsonParser.readValueAs(listOfTypes);
		}

	}

	private static final class TypeListSerializer extends StdSerializer<List<GraphSchema.Type>> {

		@Serial
		private static final long serialVersionUID = -8831424337461613203L;

		TypeListSerializer() {
			super(TypeFactory.defaultInstance().constructType(new TypeReference<List<GraphSchema.Type>>() {
			}));
		}

		@Override
		public void serialize(List<GraphSchema.Type> types, JsonGenerator gen, SerializerProvider serializerProvider) throws IOException {
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
