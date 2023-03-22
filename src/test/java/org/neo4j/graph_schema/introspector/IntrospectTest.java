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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IntrospectTest {

	private Neo4j embeddedDatabaseServer;

	@BeforeAll
	void initializeNeo4j() throws IOException {

		var sw = new StringWriter();
		try (var in = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/movie.cypher")))) {
			in.transferTo(sw);
			sw.flush();
		}

		this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
			.withDisabledServer()
			// language=cypher
			.withFixture("""
				UNWIND range(1, 5) AS i
				WITH i CREATE (n:SomeNode {idx: i})
				""")
			// language=cypher
			.withFixture("""
				CREATE (:Actor:Person {name: 'Weird Id1', id: 'abc'})
				CREATE (:Actor:Person {name: 'Weird Id2', id: 4711})
				CREATE (:Actor:Person {name: 'Weird Id3', id: ["pt1", "pt2"]})
				CREATE (:Actor:Person {name: 'Weird Id4', id: [21, 23, 42]})
				CREATE (:Actor:Person {name: 'A compromise id', id: [0.5]})
				CREATE (:Actor:Person {name: 'A number', f: 0.5})
				CREATE (:Actor:Person {name: 'Another number', f: 50})
				CREATE (:Actor:Person {name: 'A point', p: point({latitude:toFloat('13.43'), longitude:toFloat('56.21')})})
				CREATE (:L1:`L ``2`) - [:RELATED_TO {since: datetime()}] -> (:L2:L3)
				CREATE (:L1:L2) - [:RELATED_TO {since: datetime()}] -> (:L2:L3)
				CREATE (:L1:L2) - [:RELATED_TO {since: datetime()}] -> (:Unrelated)
				CREATE (:Person) -[:REVIEWED] ->(:Book)
				CREATE (a:Wurst) -[:```HAT_DEN`] -> (:Salat)
				""")
			.withProcedure(Introspect.class)
			.build();
	}

	@AfterAll
	void closeNeo4j() {
		this.embeddedDatabaseServer.close();
	}

	@Test
	void smokeTest() {

		// language=json
		var expected = """
		{
		  "graphSchemaRepresentation" : {
		    "graphSchema" : {
		      "nodeLabels" : [ {
		        "$id" : "nl:L1",
		        "token" : "L1"
		      }, {
		        "$id" : "nl:L `2",
		        "token" : "`L ``2`"
		      }, {
		        "$id" : "nl:Wurst",
		        "token" : "Wurst"
		      }, {
		        "$id" : "nl:L2",
		        "token" : "L2"
		      }, {
		        "$id" : "nl:Book",
		        "token" : "Book"
		      }, {
		        "$id" : "nl:Actor",
		        "token" : "Actor"
		      }, {
		        "$id" : "nl:L3",
		        "token" : "L3"
		      }, {
		        "$id" : "nl:Unrelated",
		        "token" : "Unrelated"
		      }, {
		        "$id" : "nl:Person",
		        "token" : "Person"
		      }, {
		        "$id" : "nl:Salat",
		        "token" : "Salat"
		      }, {
		        "$id" : "nl:SomeNode",
		        "token" : "SomeNode"
		      } ],
		      "relationshipTypes" : [ {
		        "$id" : "rt:`HAT_DEN",
		        "token" : "```HAT_DEN`"
		      }, {
		        "$id" : "rt:REVIEWED",
		        "token" : "REVIEWED"
		      }, {
		        "$id" : "rt:RELATED_TO",
		        "token" : "RELATED_TO"
		      } ],
		      "nodeObjectTypes" : [ {
		        "labels" : [ {
		          "$ref" : "#nl:Actor"
		        }, {
		          "$ref" : "#nl:Person"
		        } ],
		        "properties" : [ {
		          "token" : "name",
		          "type" : {
		            "type" : "string"
		          },
		          "nullable" : false
		        }, {
		          "token" : "id",
		          "type" : [ {
		            "type" : "array",
		            "items" : {
		              "type" : "integer"
		            }
		          }, {
		            "type" : "array",
		            "items" : {
		              "type" : "string"
		            }
		          }, {
		            "type" : "integer"
		          }, {
		            "type" : "string"
		          }, {
		            "type" : "array",
		            "items" : {
		              "type" : "float"
		            }
		          } ],
		          "nullable" : true
		        }, {
		          "token" : "f",
		          "type" : [ {
		            "type" : "integer"
		          }, {
		            "type" : "float"
		          } ],
		          "nullable" : true
		        }, {
		          "token" : "p",
		          "type" : {
		            "type" : "point"
		          },
		          "nullable" : true
		        } ],
		        "$id" : "n:Actor:Person"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:Book"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:Book"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:L `2"
		        }, {
		          "$ref" : "#nl:L1"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:L `2:L1"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:L1"
		        }, {
		          "$ref" : "#nl:L2"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:L1:L2"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:L2"
		        }, {
		          "$ref" : "#nl:L3"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:L2:L3"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:Person"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:Person"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:Salat"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:Salat"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:SomeNode"
		        } ],
		        "properties" : [ {
		          "token" : "idx",
		          "type" : {
		            "type" : "integer"
		          },
		          "nullable" : false
		        } ],
		        "$id" : "n:SomeNode"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:Unrelated"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:Unrelated"
		      }, {
		        "labels" : [ {
		          "$ref" : "#nl:Wurst"
		        } ],
		        "properties" : [ ],
		        "$id" : "n:Wurst"
		      } ],
		      "relationshipObjectTypes" : [ {
		        "type" : {
		          "$ref" : "#rt:RELATED_TO"
		        },
		        "from" : {
		          "$ref" : "#n:L `2:L1"
		        },
		        "to" : {
		          "$ref" : "#n:L2:L3"
		        },
		        "properties" : [ {
		          "token" : "since",
		          "type" : {
		            "type" : "datetime"
		          },
		          "nullable" : false
		        }, {
		          "token" : "since",
		          "type" : {
		            "type" : "datetime"
		          },
		          "nullable" : false
		        } ],
		        "$id" : "r:RELATED_TO"
		      }, {
		        "type" : {
		          "$ref" : "#rt:RELATED_TO"
		        },
		        "from" : {
		          "$ref" : "#n:L1:L2"
		        },
		        "to" : {
		          "$ref" : "#n:Unrelated"
		        },
		        "properties" : [ {
		          "token" : "since",
		          "type" : {
		            "type" : "datetime"
		          },
		          "nullable" : false
		        } ],
		        "$id" : "r:RELATED_TO_1"
		      }, {
		        "type" : {
		          "$ref" : "#rt:REVIEWED"
		        },
		        "from" : {
		          "$ref" : "#n:Person"
		        },
		        "to" : {
		          "$ref" : "#n:Book"
		        },
		        "properties" : [ ],
		        "$id" : "r:REVIEWED"
		      }, {
		        "type" : {
		          "$ref" : "#rt:`HAT_DEN"
		        },
		        "from" : {
		          "$ref" : "#n:Wurst"
		        },
		        "to" : {
		          "$ref" : "#n:Salat"
		        },
		        "properties" : [ ],
		        "$id" : "r:`HAT_DEN"
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
}
