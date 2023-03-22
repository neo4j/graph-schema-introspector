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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SamplingTest {

	private Neo4j embeddedDatabaseServer;

	@BeforeAll
	void initializeNeo4j() {

		this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
			.withDisabledServer()
			.withFixture(
				"CREATE (:A1) -[:A_TYPE {x: 'hallo'}]-> (:B1)\n".repeat((int) (GraphSchema.Introspector.DEFAULT_SAMPLE_SIZE  * 10)) +
				"CREATE (:A2) -[:A_TYPE {x: 'hallo'}]-> (:B2)\n"
			)
			.withProcedure(Introspect.class)
			.build();
	}

	@AfterAll
	void closeNeo4j() {
		this.embeddedDatabaseServer.close();
	}

	@Test
	void samplingShouldWork() {

		// language=json
		var expected = """
		{
		  "graphSchemaRepresentation" : {
		    "graphSchema" : {
		      "nodeLabels" : [ {
		        "$id" : "nl:A1",
		        "token" : "A1"
		      }, {
		        "$id" : "nl:B2",
		        "token" : "B2"
		      }, {
		        "$id" : "nl:A2",
		        "token" : "A2"
		      }, {
		        "$id" : "nl:B1",
		        "token" : "B1"
		      } ],
		      "relationshipTypes" : [ {
		        "$id" : "rt:A_TYPE",
		        "token" : "A_TYPE"
		      } ],
		      "nodeObjectTypes" : [ {
		        "labels" : [ {
		          "$ref" : "#nl:A1"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:A1"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:A2"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:A2"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:B1"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:B1"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:B2"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:B2"
		      } ],
		      "relationshipObjectTypes" : [ {
		        "type" : {
		          "$ref" : "#rt:A_TYPE"
		        },
		        "from" : {
		          "$ref" : "#n:A1"
		        },
		        "to" : {
		          "$ref" : "#n:B1"
		        },
		        "properties" : [ {
		          "token" : "x",
		          "type" : {
		            "type" : "string"
		          },
		          "nullable" : false
		        } ],
		        "$id" : "r:A_TYPE"
		      } ]
		    }
		  }
		}""";

		try (
			var driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
			var session = driver.session()
		) {

			var result = session.run("CALL experimental.introspect.asJson({useConstantIds: true, prettyPrint: true}) YIELD value RETURN value AS result").single().get("result").asString();
			assertThat(result).isEqualTo(expected);
		}
	}

	@Test
	void samplingCanBeDisabled() {

		// language=json
		var expected = """
		{
		  "graphSchemaRepresentation" : {
		    "graphSchema" : {
		      "nodeLabels" : [ {
		        "$id" : "nl:A1",
		        "token" : "A1"
		      }, {
		        "$id" : "nl:B2",
		        "token" : "B2"
		      }, {
		        "$id" : "nl:A2",
		        "token" : "A2"
		      }, {
		        "$id" : "nl:B1",
		        "token" : "B1"
		      } ],
		      "relationshipTypes" : [ {
		        "$id" : "rt:A_TYPE",
		        "token" : "A_TYPE"
		      } ],
		      "nodeObjectTypes" : [ {
		        "labels" : [ {
		          "$ref" : "#nl:A1"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:A1"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:A2"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:A2"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:B1"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:B1"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:B2"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:B2"
		      } ],
		      "relationshipObjectTypes" : [ {
		        "type" : {
		          "$ref" : "#rt:A_TYPE"
		        },
		        "from" : {
		          "$ref" : "#n:A1"
		        },
		        "to" : {
		          "$ref" : "#n:B1"
		        },
		        "properties" : [ {
		          "token" : "x",
		          "type" : {
		            "type" : "string"
		          },
		          "nullable" : false
		        } ],
		        "$id" : "r:A_TYPE"
		      }, {
		        "type" : {
		          "$ref" : "#rt:A_TYPE"
		        },
		        "from" : {
		          "$ref" : "#n:A2"
		        },
		        "to" : {
		          "$ref" : "#n:B2"
		        },
		        "properties" : [ {
		          "token" : "x",
		          "type" : {
		            "type" : "string"
		          },
		          "nullable" : false
		        } ],
		        "$id" : "r:A_TYPE_1"
		      } ]
		    }
		  }
		}""";

		try (
			var driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
			var session = driver.session()
		) {

			var result = session.run("CALL experimental.introspect.asJson({useConstantIds: true, prettyPrint: true, sampleOnly: false}) YIELD value RETURN value AS result").single().get("result").asString();
			assertThat(result).isEqualTo(expected);
		}
	}
}
