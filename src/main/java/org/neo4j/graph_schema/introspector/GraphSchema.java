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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.graph_schema.introspector.Introspect.Config;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Resource;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;

import com.github.f4b6a3.tsid.TsidFactory;

/**
 * The schema derived.
 */
final class GraphSchema {

	static GraphSchema from(Transaction transaction, Config config) throws Exception {
		return new Introspector(transaction, config).buildSchema();
	}

	private final Map<String, Token> nodeLabels;
	private final Map<String, Token> relationshipTypes;

	private final Map<String, GraphSchema.NodeObjectType> nodeObjectTypes;
	private final Map<String, RelationshipObjectType> relationshipObjectTypes;

	private GraphSchema(Map<String, Token> nodeLabels, Map<String, Token> relationshipTypes, Map<String, NodeObjectType> nodeObjectTypes, Map<String, RelationshipObjectType> relationshipObjectTypes) {
		this.nodeLabels = nodeLabels;
		this.relationshipTypes = relationshipTypes;
		this.nodeObjectTypes = nodeObjectTypes;
		this.relationshipObjectTypes = relationshipObjectTypes;
	}

	public Map<String, Token> nodeLabels() {
		return nodeLabels;
	}

	public Map<String, Token> relationshipTypes() {
		return relationshipTypes;
	}

	public Map<String, NodeObjectType> nodeObjectTypes() {
		return nodeObjectTypes;
	}

	public Map<String, RelationshipObjectType> relationshipObjectTypes() {
		return relationshipObjectTypes;
	}

	record Type(String value, String itemType) {
	}

	record Property(String token, List<Type> types, boolean mandatory) {
	}

	record NodeObjectType(String id, List<Ref> labels, List<Property> properties) {

		NodeObjectType(String id, List<Ref> labels) {
			this(id, labels, new ArrayList<>()); // Mutable on purpose
		}
	}

	record Token(String id, String value) {
	}

	record Ref(String value) {
	}

	record RelationshipObjectType(String id, Ref type, Ref from, Ref to, List<GraphSchema.Property> properties) {

		RelationshipObjectType(String id, Ref type, Ref from, Ref to) {
			this(id, type, from, to, new ArrayList<>()); // Mutable on purpose
		}
	}

	private static class Introspector {

		private static final Supplier<String> TSID_FACTORY = new Supplier<String>() {
			private final TsidFactory internalFactory = TsidFactory.builder()
				.withRandomFunction(length -> {
					final byte[] bytes = new byte[length];
					ThreadLocalRandom.current().nextBytes(bytes);
					return bytes;
				}).build();

			@Override
			public String get() {
				return internalFactory.create().format("%S");
			}
		};

		private static final Pattern ENCLOSING_TICK_MARKS = Pattern.compile("^`(.+)`$");
		private static final Map<String, String> TYPE_MAPPING = Map.of(
			"Long", "integer",
			"Double", "float"
		);

		private final Transaction transaction;

		private final Config config;

		private Introspector(Transaction transaction, Config config) {
			this.transaction = transaction;
			this.config = config;
		}

		GraphSchema buildSchema() throws Exception {
			var nodeLabels = getNodeLabels();
			var relationshipTypes = getRelationshipTypes();

			var nodeObjectTypeIdGenerator = new CachingUnaryOperator<>(new NodeObjectIdGenerator(config.useConstantIds()));
			var relationshipObjectIdGenerator = new RelationshipObjectIdGenerator(config.useConstantIds());

			var nodeObjectTypes = getNodeObjectTypes(nodeObjectTypeIdGenerator, nodeLabels);
			var relationshipObjectTypes = getRelationshipObjectTypes(nodeObjectTypeIdGenerator, relationshipObjectIdGenerator, relationshipTypes);

			return new GraphSchema(nodeLabels, relationshipTypes, nodeObjectTypes, relationshipObjectTypes);
		}

		private Map<String, Token> getNodeLabels() throws Exception {

			return getToken(transaction.getAllLabelsInUse(), Label::name, config.quoteTokens(), config.useConstantIds() ? "nl:%s"::formatted : ignored -> TSID_FACTORY.get());
		}

		private Map<String, Token> getRelationshipTypes() throws Exception {

			return getToken(transaction.getAllRelationshipTypesInUse(), RelationshipType::name, config.quoteTokens(), config.useConstantIds() ? "rt:%s"::formatted : ignored -> TSID_FACTORY.get());
		}

		private <T> Map<String, Token> getToken(Iterable<T> tokensInUse, Function<T, String> nameExtractor, boolean quoteTokens, UnaryOperator<String> idGenerator) throws Exception {

			Function<Token, Token> valueMapper = Function.identity();
			if (quoteTokens) {
				valueMapper = token -> new Token(token.id(), SchemaNames.sanitize(token.value()).orElse(token.value()));
			}
			try {
				return StreamSupport.stream(tokensInUse.spliterator(), false)
					.map(label -> {
						var tokenValue = nameExtractor.apply(label);
						return new Token(idGenerator.apply(tokenValue), tokenValue);
					})
					.collect(Collectors.toMap(Token::value, valueMapper));
			} finally {
				if (tokensInUse instanceof Resource resource) {
					resource.close();
				}
			}
		}

		/**
		 * The main algorithm of retrieving node object types (or instances). It uses the existing procedure {@code db.schema.nodeTypeProperties}
		 * for building a map from nodeType to property sets.
		 *
		 * @param idGenerator The id generator
		 * @param labelIdToToken The map of existing token by id
		 * @return A map with the node object instances
		 * @throws Exception Any exception that might occur
		 */
		private Map<String, GraphSchema.NodeObjectType> getNodeObjectTypes(UnaryOperator<String> idGenerator, Map<String, Token> labelIdToToken) throws Exception {

			if (labelIdToToken.isEmpty()) {
				return Map.of();
			}

			// language=cypher
			var query = """
				CALL db.schema.nodeTypeProperties()
				YIELD nodeType, nodeLabels, propertyName, propertyTypes, mandatory
				RETURN *
				ORDER BY nodeType ASC
				""";

			var nodeObjectTypes = new LinkedHashMap<String, NodeObjectType>();
			transaction.execute(query).accept((Result.ResultVisitor<Exception>) resultRow -> {
				@SuppressWarnings("unchecked")
				var nodeLabels = ((List<String>) resultRow.get("nodeLabels")).stream().sorted().toList();

				var id = idGenerator.apply(resultRow.getString("nodeType"));
				var nodeObject = nodeObjectTypes.computeIfAbsent(id, key -> new GraphSchema.NodeObjectType(key, nodeLabels
					.stream().map(l -> new Ref(labelIdToToken.get(l).id)).toList()));
				extractProperty(resultRow)
					.ifPresent(nodeObject.properties()::add);

				return true;
			});
			return nodeObjectTypes;
		}

		/**
		 * The main algorithm of retrieving node object types (or instances). It uses the existing procedure {@literal db.schema.relTypeProperties}
		 * for building a map from types to property sets.
		 * <p>
		 * It does a full label scan.
		 *
		 * @param nodeObjectTypeIdGenerator The id generator f or node objects
		 * @param idGenerator               The id generator for relationships
		 * @param relationshipIdToToken     The map of existing token by id
		 * @return A map with the relationship object instances
		 * @throws Exception Any exception that might occur
		 */
		private Map<String, RelationshipObjectType> getRelationshipObjectTypes(
			UnaryOperator<String> nodeObjectTypeIdGenerator,
			BinaryOperator<String> idGenerator,
			Map<String, Token> relationshipIdToToken
		) throws Exception {

			if (relationshipIdToToken.isEmpty()) {
				return Map.of();
			}

			// language=cypher
			var query = """
				CALL db.schema.relTypeProperties() YIELD relType, propertyName, propertyTypes, mandatory
				WITH substring(relType, 2, size(relType)-3) AS relType, propertyName, propertyTypes, mandatory
				MATCH (n)-[r]->(m) WHERE type(r) = relType
				WITH DISTINCT labels(n) AS from, labels(m) AS to, relType, propertyName, propertyTypes, mandatory
				RETURN *
				ORDER BY relType ASC
				""";

			var relationshipObjectTypes = new LinkedHashMap<String, RelationshipObjectType>();

			transaction.execute(query).accept((Result.ResultVisitor<Exception>) resultRow -> {
				var relType = resultRow.getString("relType");
				@SuppressWarnings("unchecked")
				var from = nodeObjectTypeIdGenerator.apply(":" + ((List<String>) resultRow.get("from")).stream()
					.sorted()
					.map(v -> "`" + v + "`")
					.collect(Collectors.joining(":")));
				@SuppressWarnings("unchecked")
				var to = nodeObjectTypeIdGenerator.apply(":" + ((List<String>) resultRow.get("to")).stream()
					.sorted()
					.map(v -> "`" + v + "`")
					.collect(Collectors.joining(":")));

				var id = idGenerator.apply(relType, to);
				var relationshipObject = relationshipObjectTypes.computeIfAbsent(id, key ->
					new RelationshipObjectType(key, new Ref(relationshipIdToToken.get(relType).id()), new Ref(from), new Ref(to)));
				extractProperty(resultRow)
					.ifPresent(relationshipObject.properties()::add);

				return true;
			});
			return relationshipObjectTypes;
		}

		Optional<Property> extractProperty(Result.ResultRow resultRow) {
			var propertyName = resultRow.getString("propertyName");
			if (propertyName == null) {
				return Optional.empty();
			}

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
					return new GraphSchema.Type(type, itemType);
				}).toList();

			return Optional.of(new GraphSchema.Property(propertyName, types, resultRow.getBoolean("mandatory")));
		}

		private static String splitStripAndJoin(String value, String prefix) {
			return Arrays.stream(value.split(":"))
				.map(String::trim)
				.filter(Predicate.not(String::isBlank))
				.map(t -> ENCLOSING_TICK_MARKS.matcher(t).replaceAll(m -> m.group(1)))
				.collect(Collectors.joining(":", prefix + ":", ""));
		}

		private static class NodeObjectIdGenerator implements UnaryOperator<String> {

			private final boolean useConstantIds;

			NodeObjectIdGenerator(boolean useConstantIds) {
				this.useConstantIds = useConstantIds;
			}

			@Override
			public String apply(String nodeType) {

				if (useConstantIds) {
					return splitStripAndJoin(nodeType, "n");
				}

				return TSID_FACTORY.get();
			}
		}


		/**
		 * Not thread safe.
		 */
		private static class RelationshipObjectIdGenerator implements BinaryOperator<String> {

			private final boolean useConstantIds;
			private final Map<String, Map<String, Integer>> counter = new HashMap<>();

			RelationshipObjectIdGenerator(boolean useConstantIds) {
				this.useConstantIds = useConstantIds;
			}

			@Override
			public String apply(String relType, String target) {

				if (useConstantIds) {
					var id = splitStripAndJoin(relType, "r");
					var count = counter.computeIfAbsent(id, ignored -> new HashMap<>());
					if (count.isEmpty()) {
						count.put(target, 0);
						return id;
					} else if (count.containsKey(target)) {
						var value = count.get(target);
						return value == 0 ? id : id + "_" + value;
					} else {
						var newValue = count.size();
						count.put(target, newValue);
						return id + "_" + newValue;
					}
				}

				return TSID_FACTORY.get();
			}
		}
	}

	/**
	 * Not thread safe.
	 */
	private static class CachingUnaryOperator<T> implements UnaryOperator<T> {

		private final Map<T, T> cache = new HashMap<>();
		private final UnaryOperator<T> delegate;

		CachingUnaryOperator(UnaryOperator<T> delegate) {
			this.delegate = delegate;
		}

		@Override
		public T apply(T s) {
			return cache.computeIfAbsent(s, delegate);
		}
	}
}
