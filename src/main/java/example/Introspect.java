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

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.UserFunction;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.f4b6a3.tsid.TsidFactory;

/**
 * UDF for creating a Graph database schema according to the format defined here:
 *
 * <a href="https://github.com/neo4j/graph-schema-json-js-utils">graph-schema-json-js-utils</a>, see scheme
 * <a href="https://github.com/neo4j/graph-schema-json-js-utils/blob/main/json-schema.json">here</a> and an example
 * <a href="https://github.com/neo4j/graph-schema-json-js-utils/blob/main/test/validation/test-schemas/full.json">here</a>.
 * <p>
 * It uses Time-Sorted Unique Identifiers (TSID) for the ids inside the generated schema by default.
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

	@Context
	public Transaction transaction;

	@UserFunction(name = "db.introspect")
	public String introspectSchema() throws Exception {

		var labels = getNodeLabels();
		var relationshipTypes = getRelationshipTypes();

		try (var result = new StringWriter()) {
			try (var gen = OBJECT_MAPPER.createGenerator(result)) {
				gen.writeStartObject();
				gen.writeObjectFieldStart("graphSchemaRepresentation");
				gen.writeStringField("version", GRAPH_SCHEMA_REPRESENTATION_VERSION);
				gen.writeFieldName("graphSchema");
				gen.writeStartObject();
				writeTokens(gen, "nodeLabels", labels.values());
				writeTokens(gen, "relationshipTypes", relationshipTypes.values());
				gen.writeEndObject();
				gen.writeEndObject();
				gen.writeEndObject();
			}
			result.flush();
			return result.toString();
		}
	}

	private Map<String, Token> getNodeLabels() throws Exception {

		return getToken("CALL db.labels() YIELD label", ignored -> TSID_FACTORY.create().format("nl:%S"));
	}

	private Map<String, Token> getRelationshipTypes() throws Exception {

		return getToken("CALL db.relationshipTypes() YIELD relationshipType", ignored -> TSID_FACTORY.create().format("rt:%S"));
	}

	private Map<String, Token> getToken(String query, UnaryOperator<String> idGenerator) throws Exception {

		var tokens = new ArrayList<Token>();
		transaction.execute(query + " AS value").accept((Result.ResultVisitor<Exception>) resultRow -> {
			var tokenValue = resultRow.getString("value");
			tokens.add(new Token(idGenerator.apply(tokenValue), tokenValue));
			return true;
		});
		return tokens.stream().collect(Collectors.toMap(Token::value, Function.identity()));
	}

	record Token(@JsonProperty("$id") String id, @JsonProperty("token") String value) {
	}


	private void writeTokens(JsonGenerator gen, String fieldName, Collection<Token> tokens) throws Exception {
		gen.writeArrayFieldStart(fieldName);
		for (Token token : tokens) {
			gen.writeObject(token);
		}
		gen.writeEndArray();
	}
}

