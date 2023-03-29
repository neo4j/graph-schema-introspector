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

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Wrapper for a string, needed for Neo4j Procedures.
 *
 * @param value The wrapped value
 */
public record GraphSchemaJSONResultWrapper(String value) {

	public static GraphSchemaJSONResultWrapper of(GraphSchema graphSchema, Introspect.Config config) throws JsonProcessingException {

		var objectMapper = GraphSchemaModule.getGraphSchemaObjectMapper();
		var writer = config.prettyPrint() ? objectMapper.writerWithDefaultPrettyPrinter() : objectMapper.writer();
		return new GraphSchemaJSONResultWrapper(writer.writeValueAsString(graphSchema));
	}
}
