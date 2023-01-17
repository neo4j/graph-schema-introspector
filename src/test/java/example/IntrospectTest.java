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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;

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
			.withFixture(sw.toString())
			.withFixture("""
				    UNWIND range(1, 5) AS i
				    WITH i CREATE (n:SomeNode {idx: i})
				""")
			.withFunction(Introspect.class)
			.build();
	}

	@AfterAll
	void closeNeo4j() {
		this.embeddedDatabaseServer.close();
	}

	@Test
	void joinsStrings() {
		// This is in a try-block, to make sure we close the driver after the test
		try (Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
		     Session session = driver.session()) {

			// When
			String result = session.run("RETURN db.introspect() AS result").single().get("result").asString();

			System.out.println(result);
			// Then
			assertThat(result).isEqualTo(("Hello,World"));
		}
	}
}
