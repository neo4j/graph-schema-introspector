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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Encapsulating all the Jackson-JSON stuff.
 */
final class GraphSchemaModule extends SimpleModule {

	@Serial
	private static final long serialVersionUID = -4886300307467434436L;

	GraphSchemaModule() {
		addSerializer(GraphSchema.Type.class, new TypeSerializer());
		addSerializer(GraphSchema.class, new GraphSchemaSerializer());
		addSerializer(GraphSchema.Ref.class, new RefSerializer());
		setMixInAnnotation(GraphSchema.Property.class, PropertyMixin.class);
		setMixInAnnotation(GraphSchema.NodeObjectType.class, NodeObjectTypeMixin.class);
		setMixInAnnotation(GraphSchema.Token.class, TokenMixin.class);
		setMixInAnnotation(GraphSchema.RelationshipObjectType.class, RelationshipObjectTypeMixin.class);
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

	@JsonPropertyOrder({"token", "type", "mandatory"})
	private abstract static class PropertyMixin {

		@JsonProperty("type") @JsonSerialize(using = TypeListSerializer.class)
		abstract List<GraphSchema.Type> types();

		@JsonInclude(JsonInclude.Include.NON_DEFAULT)
		abstract boolean mandatory();
	}

	private abstract static class NodeObjectTypeMixin {

		@JsonProperty("$id")
		abstract String id();
	}

	private abstract static class TokenMixin {

		@JsonProperty("$id")
		abstract String id();

		@JsonProperty("token")
		abstract String value();
	}

	private static final class RefSerializer extends StdSerializer<GraphSchema.Ref> {

		@Serial
		private static final long serialVersionUID = -3928051476420574836L;

		RefSerializer() {
			super(GraphSchema.Ref.class);
		}

		@Override
		public void serialize(GraphSchema.Ref value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeStartObject();
			gen.writeObjectField("$ref", "#" + value.value());
			gen.writeEndObject();
		}
	}

	private abstract static class RelationshipObjectTypeMixin {

		@JsonProperty("$id")
		abstract String id();
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
