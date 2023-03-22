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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;

/**
 * @author Michael J. Simons
 */
class GraphSchemaTest {

	@Nested
	class IntrospectTest {

		@Test
		void shouldSampleByDefault() throws InvocationTargetException, IllegalAccessException {

			var getRelationshipPropertiesQuery = ReflectionUtils.getRequiredMethod(GraphSchema.Introspector.class, "getRelationshipPropertiesQuery", Introspect.Config.class);
			getRelationshipPropertiesQuery.setAccessible(true);
			var query = getRelationshipPropertiesQuery.invoke(null, new Introspect.Config(Map.of()));
			assertThat(query).isEqualTo("""
				CALL db.schema.relTypeProperties() YIELD relType, propertyName, propertyTypes, mandatory
				WITH substring(relType, 2, size(relType)-3) AS relType, propertyName, propertyTypes, mandatory
				CALL {
					WITH relType, propertyName
					MATCH (n)-[r]->(m) WHERE type(r) = relType AND (r[propertyName] IS NOT NULL OR propertyName IS NULL)
					WITH n, r, m
					LIMIT 100
					WITH DISTINCT labels(n) AS from, labels(m) AS to
					RETURN from, to
				}
				RETURN DISTINCT from, to, relType, propertyName, propertyTypes, mandatory
				ORDER BY relType ASC
				""");
		}

		@Test
		void sampleCanBeDisabled() throws InvocationTargetException, IllegalAccessException {

			var getRelationshipPropertiesQuery = ReflectionUtils.getRequiredMethod(GraphSchema.Introspector.class, "getRelationshipPropertiesQuery", Introspect.Config.class);
			getRelationshipPropertiesQuery.setAccessible(true);
			var query = getRelationshipPropertiesQuery.invoke(null, new Introspect.Config(Map.of("sampleOnly", false)));
			assertThat(query).isEqualTo("""
				CALL db.schema.relTypeProperties() YIELD relType, propertyName, propertyTypes, mandatory
				WITH substring(relType, 2, size(relType)-3) AS relType, propertyName, propertyTypes, mandatory
				CALL {
					WITH relType, propertyName
					MATCH (n)-[r]->(m) WHERE type(r) = relType AND (r[propertyName] IS NOT NULL OR propertyName IS NULL)
					WITH n, r, m
					// LIMIT
					WITH DISTINCT labels(n) AS from, labels(m) AS to
					RETURN from, to
				}
				RETURN DISTINCT from, to, relType, propertyName, propertyTypes, mandatory
				ORDER BY relType ASC
				""");
		}
	}
}
