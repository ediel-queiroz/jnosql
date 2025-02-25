/*
 *  Copyright (c) 2022 Contributors to the Eclipse Foundation
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   and Apache License v2.0 which accompanies this distribution.
 *   The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 *   and the Apache License v2.0 is available at http://www.opensource.org/licenses/apache2.0.php.
 *
 *   You may elect to redistribute this code under either of these licenses.
 *
 *   Contributors:
 *
 *   Otavio Santana
 */
package org.eclipse.jnosql.mapping.graph;

import jakarta.inject.Inject;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.eclipse.jnosql.mapping.core.Converters;
import org.eclipse.jnosql.mapping.graph.entities.Animal;
import org.eclipse.jnosql.mapping.graph.spi.GraphExtension;
import org.eclipse.jnosql.mapping.reflection.Reflections;
import org.eclipse.jnosql.mapping.core.spi.EntityMetadataExtension;
import org.jboss.weld.junit5.auto.AddExtensions;
import org.jboss.weld.junit5.auto.AddPackages;
import org.jboss.weld.junit5.auto.EnableAutoWeld;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

@EnableAutoWeld
@AddPackages(value = {Converters.class, Transactional.class})
@AddPackages(BookRepository.class)
@AddPackages(Reflections.class)
@AddExtensions({EntityMetadataExtension.class, GraphExtension.class})
class DefaultEntityTreeTest {

    @Inject
    protected GraphTemplate graphTemplate;

    @Inject
    protected Graph graph;

    private Animal lion;
    private Animal zebra;
    private Animal giraffe;
    private Animal grass;

    @BeforeEach
    void setUp() {
        graph.traversal().V().toList().forEach(Vertex::remove);
        graph.traversal().E().toList().forEach(Edge::remove);
        lion = graphTemplate.insert(new Animal("lion"));
        zebra = graphTemplate.insert(new Animal("zebra"));
        giraffe = graphTemplate.insert(new Animal("giraffe"));
        grass = graphTemplate.insert(new Animal("grass"));

        graphTemplate.edge(lion, "eats", giraffe);
        graphTemplate.edge(lion, "eats", zebra);
        graphTemplate.edge(zebra, "eats", grass);
        graphTemplate.edge(giraffe, "eats", grass);
    }

    @Test
    void shouldCreateLeaf() {
        EntityTree tree = graphTemplate.traversalVertex()
                .hasLabel(Animal.class)
                .in("eats")
                .tree();

        List<Animal> animals = tree.<Animal>getLeaf()
                .sorted(comparing(Animal::getName))
                .collect(toList());

        assertNotNull(animals);
        assertFalse(animals.isEmpty());
        assertEquals(4, animals.size());
        assertArrayEquals(animals.toArray(new Animal[4]),
                asList(giraffe, lion, lion, zebra).toArray(new Animal[4]));

    }

    @Test
    void shouldGetLeafTrees() {
        EntityTree tree = graphTemplate.traversalVertex()
                .hasLabel(Animal.class)
                .in("eats")
                .in("eats")
                .tree();

        List<EntityTree> trees = tree.getLeafTrees()
                .collect(toList());

        assertNotNull(trees);
        assertFalse(trees.isEmpty());
        assertEquals(2, trees.size());

        List<Animal> animals = trees.stream()
                .<Animal>flatMap(EntityTree::getRoots)
                .distinct().toList();

        assertEquals(1, animals.size());
        assertEquals(lion, animals.get(0));
    }

    @Test
    void shouldGetParents() {
        EntityTree tree = graphTemplate.traversalVertex()
                .hasLabel(Animal.class)
                .in("eats")
                .in("eats")
                .tree();

        List<Animal> animals = tree.<Animal>getRoots().toList();
        List<Entry<Long, Animal>> parentsIds = tree.<Long, Animal>getRootsIds()
                .toList();
        assertEquals(animals.size(), parentsIds.size());

        Animal animal = animals.get(0);
        Entry<Long, Animal> entry = parentsIds.get(0);

        assertEquals(animal, entry.getValue());
        assertEquals(animal.getId(), entry.getKey());

    }

    @Test
    void shouldGetParentId() {
        EntityTree tree = graphTemplate.traversalVertex()
                .hasLabel(Animal.class)
                .out("eats")
                .out("eats")
                .tree();

        Entry<Long, Animal> entry = tree.<Long, Animal>getRootsIds()
                .findFirst().get();

        assertEquals(lion, entry.getValue());

        EntityTree subTree = tree.getTreeFromRoot(entry.getKey()).get();
        Animal[] animals = subTree.<Animal>getRoots()
                .sorted(comparing(Animal::getName))
                .toArray(Animal[]::new);
        assertArrayEquals(Stream.of(giraffe, zebra).toArray(Animal[]::new), animals);
        System.out.println(subTree);
        Animal animal = subTree.<Animal>getLeaf().distinct().findFirst().get();
        assertEquals(grass, animal);
    }

    @Test
    void shouldIsLeaf() {
        EntityTree tree = graphTemplate.traversalVertex()
                .hasLabel(Animal.class)
                .out("eats")
                .tree();

        assertFalse(tree.isLeaf());
        EntityTree subTree = tree.getTreeFromRoot(lion.getId()).get();

        assertTrue(subTree.isLeaf());
    }

    @Test
    void shouldGetTreesAtDepth() {
        EntityTree tree = graphTemplate.traversalVertex()
                .hasLabel(Animal.class)
                .out("eats")
                .out("eats")
                .tree();

        assertEquals(1L, tree.getTreesAtDepth(1).count());
        assertEquals(1L, tree.getTreesAtDepth(2).count());
        assertEquals(2L, tree.getTreesAtDepth(3).count());
    }

    @Test
    void shouldGetLeafsAtDepth() {
        EntityTree tree = graphTemplate.traversalVertex()
                .hasLabel(Animal.class)
                .out("eats")
                .out("eats")
                .tree();

        List<Animal> animals = tree.<Animal>getLeafsAtDepth(1).toList();
        assertEquals(1, animals.size());
        assertEquals(lion, animals.get(0));

        List<Animal> animals2 = tree.<Animal>getLeafsAtDepth(2).toList();
        assertEquals(2, animals2.size());
        assertArrayEquals(Stream.of(zebra, giraffe).toArray(Animal[]::new), animals2.toArray(new Animal[2]));

        List<Animal> animals3 = tree.<Animal>getLeafsAtDepth(3).distinct().toList();
        assertEquals(1, animals3.size());
        assertArrayEquals(Stream.of(grass).toArray(Animal[]::new), animals3.toArray(new Animal[1]));
    }
}

