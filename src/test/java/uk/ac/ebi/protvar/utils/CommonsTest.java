package uk.ac.ebi.protvar.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CommonsTest {

  @Nested
  class Strings {
    @Nested
    class emptyOrString {

      @Test
      void whenNull_returnEmptyString() {
        assertEquals("", Commons.emptyOrString(null));
      }

      @ParameterizedTest
      @ValueSource(
        strings = {
          "a",
          "AB",
          "a%^b",
          "()+^£$^%%",
          "aB",
          "Ab",
          "",
          "null",
          "   ",
          "123432"
        })
      void notNull_returnAsItIs(String test) {
        assertEquals(test, Commons.emptyOrString(test));
      }
    }

    @Nested
    class nullOrEmpty {
      @Test
      void whenNull_returnTrue() {
        assertTrue(Commons.nullOrEmpty((String) null));
      }

      @Test
      void whenEmpty_returnTrue() {
        assertTrue(Commons.nullOrEmpty(""));
      }

      @Test
      void whenBlank_returnTrue() {
        assertTrue(Commons.nullOrEmpty("  "));
      }

      @ParameterizedTest
      @ValueSource(
        strings = {"1", "AB", "a%^b", "()+^£$^%%", "aB", "Ab", " s ", "NULL", "123432"})
      void nonEmpty_shouldAlwaysReturnFalse(String test) {
        assertFalse(Commons.nullOrEmpty(test));
      }
    }

    @Nested
    class upperFirstChar {
      @Test
      void whenNullOrEmpty_returnSame() {
        assertAll(
          () -> assertNull(Commons.upperFirstChar(null)),
          () -> assertEquals("", Commons.upperFirstChar("")));
      }

      @Test
      void capitalizeFirstChar() {
        assertAll(
          () -> assertEquals("Protein", Commons.upperFirstChar("protein")),
          () -> assertEquals(" protein", Commons.upperFirstChar(" protein")),
          () -> assertEquals("Protein abc", Commons.upperFirstChar("protein abc")),
          () -> assertEquals("$protein", Commons.upperFirstChar("$protein")),
          () -> assertEquals("P!rotein", Commons.upperFirstChar("p!rotein")),
          () -> assertEquals("Protein Def", Commons.upperFirstChar("protein Def")),
          () -> assertEquals("12345", Commons.upperFirstChar("12345")));
      }
    }

    @Nested
    class lowerFirstChar {
      @Test
      void whenNullOrEmpty_returnSame() {
        assertAll(
          () -> assertNull(Commons.lowerFirstChar(null)),
          () -> assertEquals("", Commons.lowerFirstChar("")));
      }

      @Test
      void uncapitalizeFirstChar() {
        String str = "Protein";
        String result = Commons.lowerFirstChar(str);
        assertEquals("protein", result);
        assertAll(
          () -> assertEquals("protein", Commons.lowerFirstChar("Protein")),
          () -> assertEquals(" protein", Commons.lowerFirstChar(" protein")),
          () -> assertEquals("protein abc", Commons.lowerFirstChar("Protein abc")),
          () -> assertEquals("$protein", Commons.lowerFirstChar("$protein")),
          () -> assertEquals("p!rotein", Commons.lowerFirstChar("P!rotein")),
          () -> assertEquals("protein Def", Commons.lowerFirstChar("Protein Def")),
          () -> assertEquals("12345", Commons.lowerFirstChar("12345")));
      }
    }

    @Nested
    class notNullOrEmpty {

      @Test
      void whenNullOrEmpty_returnFalse() {
        assertAll(
          () -> assertFalse(Commons.notNullNotEmpty((String) null)),
          () -> assertFalse(Commons.notNullNotEmpty("   ")),
          () -> assertFalse(Commons.notNullNotEmpty("")));
      }

      @ParameterizedTest
      @ValueSource(
        strings = {"1", "AB", "a%^b", "()+^£$^%%", "aB", "Ab", " s ", "NULL", "123432"})
      void nonEmpty_shouldAlwaysReturnTrue(String test) {
        assertTrue(Commons.notNullNotEmpty(test));
      }
    }

    @Nested
    class upperFirstRemainingLower {
      @Test
      void whenNullOrEmpty_returnFalse() {
        assertAll(
          () -> assertNull(Commons.upperFirstRemainingLower(null)),
          () -> assertEquals("   ", Commons.upperFirstRemainingLower("   ")),
          () -> assertEquals("", Commons.upperFirstRemainingLower("")));
      }

      @Test
      void singleCharacter() {
        assertAll(
          () -> assertEquals("A", Commons.upperFirstRemainingLower("a")),
          () -> assertEquals("B", Commons.upperFirstRemainingLower("B")));
      }

      @Test
      void twoCharacters() {
        assertAll(
          () -> assertEquals("Ab", Commons.upperFirstRemainingLower("ab")),
          () -> assertEquals("Bc", Commons.upperFirstRemainingLower("BC")));
      }
    }
  }

  @Nested
  class Lists {
    @Nested
    class notNullOrEmpty {
      @Test
      void whenNullOrEmpty_returnFalse() {
        assertAll(
          () -> assertFalse(Commons.notNullNotEmpty((Collection) null)),
          () -> assertFalse(Commons.notNullNotEmpty(Collections.emptyList())));
      }

      @Test
      void nonEmpty_shouldAlwaysReturnTrue() {
        List<String> list = new LinkedList<>();
        list.add("test1");
        list.add("test2");
        assertAll(
          () -> assertTrue(Commons.notNullNotEmpty(Collections.singletonList(123))),
          () ->
            assertTrue(
              Commons.notNullNotEmpty(
                Collections.checkedList(list, String.class))),
          () -> assertTrue(Commons.notNullNotEmpty(list)),
          () -> assertTrue(Commons.notNullNotEmpty(Collections.synchronizedList(list))),
          () ->
            assertTrue(
              Commons.notNullNotEmpty(Collections.unmodifiableList(list))));
      }
    }

    @Nested
    class nullOrEmpty {
      @Test
      void whenNullOrEmpty_returnTrue() {
        assertTrue(Commons.nullOrEmpty((Collection) null));
        assertTrue(Commons.nullOrEmpty(Collections.emptyList()));
      }

      @Test
      void nonEmpty_shouldAlwaysReturnFalse() {
        List<Integer> list = new LinkedList<>();
        list.add(-12321);
        list.add(454353);
        assertAll(
          () -> assertFalse(Commons.nullOrEmpty(Collections.singletonList("test"))),
          () ->
            assertFalse(
              Commons.nullOrEmpty(
                Collections.checkedList(list, Integer.class))),
          () -> assertFalse(Commons.nullOrEmpty(list)),
          () -> assertFalse(Commons.nullOrEmpty(Collections.synchronizedList(list))),
          () -> assertFalse(Commons.nullOrEmpty(Collections.unmodifiableList(list))));
      }
    }

    @Nested
    class unmodifiableList {
      @Test
      void passingNullReturnEmptyList() {
        List l = Commons.unmodifiableList(null);
        assertTrue(l.isEmpty());
      }

      @Test
      void passing_emptyList_returnEmptyList() {
        List l = Commons.unmodifiableList(new ArrayList<>());
        assertTrue(l.isEmpty());
      }

      @Test
      void passingNullReturnEmptyList_unmodifiable() {
        List<String> l = Commons.unmodifiableList(null);
        assertThrows(UnsupportedOperationException.class, () -> l.add("abc"));
      }

      @Test
      void passing_emptyList_returnEmptyList_unmodifiable() {
        List<String> l = Commons.unmodifiableList(new ArrayList<>());
        assertThrows(UnsupportedOperationException.class, () -> l.add("abc"));
      }

      @Test
      void passingList_returnUnmodifiable() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        List<String> l = Commons.unmodifiableList(list);
        assertThrows(UnsupportedOperationException.class, () -> l.add("c"));
      }
    }

    @Nested
    class addOrIgnoreNull {
      @Test
      void addingNullValueInNullList_nullList() {
        List<String> l = null;
        Commons.addOrIgnoreNull(null, l);
        assertNull(l);
      }

      @Test
      void addingNotNullValueInNullList_NPE() {
        List<String> l = null;
        boolean listChanged = Commons.addOrIgnoreNull("test", l);
        assertFalse(listChanged);
      }

      @Test
      void nonNulValue() {
        List<String> l = new ArrayList<>();
        boolean listChanged = Commons.addOrIgnoreNull("abc", l);
        assertTrue(listChanged);
        assertNotNull(l);
        assertEquals(1, l.size());
        assertEquals("abc", l.get(0));
      }
    }

    @Nested
    class emptyOrList {
      @Test
      void whenNull_returnsEmptyList() {
        assertAll(
          () -> assertNotNull(Commons.emptyOrList((null))),
          () -> assertTrue(Commons.emptyOrList((null)).isEmpty()));
      }

      @Test
      void whenNotNull_shouldReturnActualList() {
        List<String> list = new LinkedList<>();
        list.add("test1");
        list.add("test2");
        List<String> synchronizedList = Collections.synchronizedList(list);
        List<String> unmodifiableList = Collections.unmodifiableList(list);
        List<String> checkedList = Collections.checkedList(list, String.class);
        List<Integer> singletonList = Collections.singletonList(123);
        assertAll(
          () -> assertSame(singletonList, Commons.emptyOrList(singletonList)),
          () -> assertSame(checkedList, Commons.emptyOrList(checkedList)),
          () -> assertSame(list, Commons.emptyOrList(list)),
          () -> assertSame(synchronizedList, Commons.emptyOrList(synchronizedList)),
          () -> assertSame(unmodifiableList, Commons.emptyOrList(unmodifiableList)));
      }
    }

    @Nested
    class modifiableList {
      @Test
      void whenNull_returnsEmptyList() {
        assertAll(
          () -> assertNotNull(Commons.modifiableList((null))),
          () -> assertTrue(Commons.modifiableList((null)).isEmpty()));
      }

      @Test
      void canAddElementsInEmptyList() {
        List<String> nullList = null;
        List<String> list = Commons.modifiableList(nullList);
        list.add("a");
        assertAll(
          () -> assertEquals("a", list.get(0)),
          () -> assertFalse(list.isEmpty()),
          () -> assertNotSame(nullList, list));
      }

      @Test
      void whenUnModifiable_returnModifiable() {
        List<String> list = new LinkedList<>();
        list.add("test1");
        list.add("test2");
        List<String> unmodifiableList = Collections.unmodifiableList(list);
        assertThrows(
          UnsupportedOperationException.class,
          () -> unmodifiableList.add("shouldNotBeAdded"));

        assertAll(
          () -> assertNotSame(list, Commons.modifiableList(list)),
          () ->
            assertNotSame(
              unmodifiableList, Commons.modifiableList(unmodifiableList)),
          () -> {
            List<String> retList = Commons.modifiableList(unmodifiableList);
            retList.add("1");
            assertEquals(3, retList.size());
            assertEquals("1", retList.get(2));
          });
      }
    }

    @Nested
    class modifiableLinkedHashSet {
      @Test
      void whenNull_returnsEmptyLinkedHashSet() {
        assertAll(
          () -> assertNotNull(Commons.modifiableLinkedHashSet((null))),
          () -> assertTrue(Commons.modifiableLinkedHashSet((null)).isEmpty()));
      }

      @Test
      void canAddElementsInEmptyLinkedHashSet() {
        LinkedHashSet<String> nullList = null;
        LinkedHashSet<String> list = Commons.modifiableLinkedHashSet(nullList);
        list.add("a");
        assertAll(
          () -> assertTrue(list.contains("a")),
          () -> assertFalse(list.isEmpty()),
          () -> assertNotSame(nullList, list));
      }

      @Test
      void canAddElementsInLinkedHashSet() {
        LinkedHashSet<String> initialList = new LinkedHashSet<>();
        initialList.add("a");
        LinkedHashSet<String> list = Commons.modifiableLinkedHashSet(initialList);
        assertAll(
          () -> assertFalse(list.isEmpty()),
          () -> assertTrue(list.contains("a")),
          () -> assertEquals(initialList, list));
      }
    }
  }

  @Nested
  class Maps {

    @Nested
    class notNullOrEmpty {
      @Test
      void whenNullOrEmpty_returnFalse() {
        assertAll(
          () -> assertFalse(Commons.notNullNotEmpty((Map) null)),
          () -> assertFalse(Commons.notNullNotEmpty(Collections.emptyMap())));
      }

      @Test
      void nonEmpty_shouldAlwaysReturnTrue() {
        Map<String, Boolean> map = new HashMap<>();
        map.put("test1", true);
        map.put("test2", false);
        assertAll(
          () -> assertTrue(Commons.notNullNotEmpty(Collections.singletonMap(1, 2))),
          () ->
            assertTrue(
              Commons.notNullNotEmpty(
                Collections.checkedMap(
                  map, String.class, Boolean.class))),
          () -> assertTrue(Commons.notNullNotEmpty(map)),
          () -> assertTrue(Commons.notNullNotEmpty(Collections.synchronizedMap(map))),
          () -> assertTrue(Commons.notNullNotEmpty(Collections.unmodifiableMap(map))));
      }
    }

    @Nested
    class nullOrEmpty {
      @Test
      void whenNullOrEmpty_returnTrue() {
        assertTrue(Commons.nullOrEmpty((Map) null));
        assertTrue(Commons.nullOrEmpty(Collections.emptyMap()));
      }

      @Test
      void nonEmpty_shouldAlwaysReturnFalse() {
        Map<Integer, Double> map = new HashMap<>();
        map.put(-12321, 1D);
        map.put(454353, 2D);
        assertAll(
          () -> assertFalse(Commons.nullOrEmpty(Collections.singletonMap("test", 1))),
          () ->
            assertFalse(
              Commons.nullOrEmpty(
                Collections.checkedMap(
                  map, Integer.class, Double.class))),
          () -> assertFalse(Commons.nullOrEmpty(map)),
          () -> assertFalse(Commons.nullOrEmpty(Collections.synchronizedMap(map))),
          () -> assertFalse(Commons.nullOrEmpty(Collections.unmodifiableMap(map))));
      }
    }

    @Nested
    class test_putOrIgnoreNull {
      @Test
      void whenAKeyIsAdded() {
        String key = "key";
        Object value = "value";
        Map<String, Object> map = new HashMap<>();
        Commons.putOrIgnoreNull(key, value, map);
        assertEquals(1, map.size());
        assertTrue(map.containsKey(key));
        assertEquals(value, map.get(key));
      }

      @Test
      void whenMoreThanOneKeysAdded() {
        String key1 = "key1";
        Object value1 = "value1";
        String key2 = "key2";
        Object value2 = "value2";
        Map<String, Object> map = new HashMap<>();
        Commons.putOrIgnoreNull(key1, value1, map);
        Commons.putOrIgnoreNull(key2, value2, map);
        assertEquals(2, map.size());
        assertTrue(map.containsKey(key1));
        assertEquals(value1, map.get(key1));
        assertTrue(map.containsKey(key2));
        assertEquals(value2, map.get(key2));
      }

      @Test
      void whenSameKeyIsAddedTwice() {
        String key1 = "key1";
        String key2 = "key2";
        String value2 = "value2";
        Object oldVal = "oldvalue";
        Object latVal = "latestvalue";
        Map<String, Object> map = new HashMap<>();
        Commons.putOrIgnoreNull(key1, oldVal, map);
        Commons.putOrIgnoreNull(key2, value2, map);
        Commons.putOrIgnoreNull(key1, latVal, map);
        assertEquals(2, map.size());
        assertTrue(map.containsKey(key1));
        assertEquals(latVal, map.get(key1));
        assertTrue(map.containsKey(key2));
        assertEquals(value2, map.get(key2));
      }

      @Test
      void whenKeyIsNull() {
        String key = null;
        Object value = "value";
        Map<String, Object> map = new HashMap<>();
        Commons.putOrIgnoreNull(key, value, map);
        assertTrue(map.isEmpty());
      }

      @Test
      void whenKeyIsEmpty() {
        String key = "";
        Object value = "value";
        Map<String, Object> map = new HashMap<>();
        Commons.putOrIgnoreNull(key, value, map);
        assertTrue(map.isEmpty());
      }

      @Test
      void whenValueIsNull() {
        String key = "key";
        Object value = null;
        Map<String, Object> map = new HashMap<>();
        Commons.putOrIgnoreNull(key, value, map);
        assertTrue(map.isEmpty());
      }

      @Test
      void whenValueIsEmpty() {
        String key = "key";
        Object value = "";
        Map<String, Object> map = new HashMap<>();
        Commons.putOrIgnoreNull(key, value, map);
        assertEquals(1, map.size());
        assertTrue(map.get(key).toString().isEmpty());
      }

      @Test
      void whenKeyAndValueAreNull() {
        String key = null;
        Object value = null;
        Map<String, Object> map = new HashMap<>();
        Commons.putOrIgnoreNull(key, value, map);
        assertTrue(map.isEmpty());
      }

      @Test
      void whenMapIsNull() {
        String key = "key";
        Object value = "value";
        Map<String, Object> map = null;
        Commons.putOrIgnoreNull(key, value, map);
        assertNull(map);
      }
    }
  }

  @Nested
  class Others {

    @Nested
    class notNull {

      @Test
      void objectsNull_ReturnFalse() {
        Object o = null;
        Collection c = null;
        Map m = null;
        Integer i = null;
        assertAll(
          () -> assertFalse(Commons.notNull(o)),
          () -> assertFalse(Commons.notNull(c)),
          () -> assertFalse(Commons.notNull(m)),
          () -> assertFalse(Commons.notNull(i)));
      }

      @Test
      void objectsNotNull_ReturnTrue() {
        Object o = new Object();
        Collection c = Collections.emptyList();
        Map m = Collections.emptyMap();
        Integer i = 5;
        assertAll(
          () -> assertTrue(Commons.notNull(o)),
          () -> assertTrue(Commons.notNull(c)),
          () -> assertTrue(Commons.notNull(m)),
          () -> assertTrue(Commons.notNull(i)));
      }
    }
  }

  @Nested
  class Sets {
    @Nested
    class notNullOrEmpty {
      @Test
      void whenNullOrEmpty_returnFalse() {
        assertAll(
          () -> assertFalse(Commons.notNullNotEmpty((Collection) null)),
          () -> assertFalse(Commons.notNullNotEmpty(Collections.emptySet())));
      }

      @Test
      void nonEmpty_shouldAlwaysReturnTrue() {
        Set<String> set = new HashSet<>();
        set.add("test1");
        set.add("test2");
        assertAll(
          () -> assertTrue(Commons.notNullNotEmpty(Collections.singleton(123))),
          () ->
            assertTrue(
              Commons.notNullNotEmpty(
                Collections.checkedSet(set, String.class))),
          () -> assertTrue(Commons.notNullNotEmpty(set)),
          () -> assertTrue(Commons.notNullNotEmpty(Collections.synchronizedSet(set))),
          () -> assertTrue(Commons.notNullNotEmpty(Collections.unmodifiableSet(set))));
      }
    }

    @Nested
    class nullOrEmpty {
      @Test
      void whenNullOrEmpty_returnTrue() {
        assertTrue(Commons.nullOrEmpty((Collection) null));
        assertTrue(Commons.nullOrEmpty(Collections.emptySet()));
      }

      @Test
      void nonEmpty_shouldAlwaysReturnFalse() {
        Set<Integer> set = new HashSet<>();
        set.add(-12321);
        set.add(454353);
        assertAll(
          () -> assertFalse(Commons.nullOrEmpty(Collections.singleton("test"))),
          () ->
            assertFalse(
              Commons.nullOrEmpty(
                Collections.checkedSet(set, Integer.class))),
          () -> assertFalse(Commons.nullOrEmpty(set)),
          () -> assertFalse(Commons.nullOrEmpty(Collections.synchronizedSet(set))),
          () -> assertFalse(Commons.nullOrEmpty(Collections.unmodifiableSet(set))));
      }
    }

    @Nested
    class unmodifiableSet {
      @Test
      void passingNullReturnEmptySet() {
        Set s = Commons.unmodifiableSet(null);
        assertTrue(s.isEmpty());
      }

      @Test
      void passing_emptySet_returnEmptyList() {
        Set s = Commons.unmodifiableSet(new HashSet<Strings>());
        assertTrue(s.isEmpty());
      }

      @Test
      void passingNullReturnEmptySet_unmodifiable() {
        Set<String> s = Commons.unmodifiableSet(null);
        assertThrows(UnsupportedOperationException.class, () -> s.add("abc"));
      }

      @Test
      void passing_emptySet_returnEmptySet_unmodifiable() {
        Set<String> s = Commons.unmodifiableSet(new HashSet<>());
        assertThrows(UnsupportedOperationException.class, () -> s.add("abc"));
      }

      @Test
      void passingList_returnUnmodifiable() {
        Set<String> set = new HashSet<>();
        set.add("a");
        set.add("b");
        Set<String> s = Commons.unmodifiableSet(set);
        assertThrows(UnsupportedOperationException.class, () -> s.add("c"));
      }
    }

    @Nested
    class addOrIgnoreNull {
      @Test
      void addingNullValueInNullSet_nullSet() {
        Set<String> s = null;
        Commons.addOrIgnoreNull(null, s);
        assertNull(s);
      }

      @Test
      void addingNotNullValueInNullSet_NPE() {
        Set<String> s = null;
        boolean setChanged = Commons.addOrIgnoreNull("test", s);
        assertFalse(setChanged);
      }

      @Test
      void nonNulValue() {
        Set<String> s = new HashSet<>();
        boolean setChanged = Commons.addOrIgnoreNull("abc", s);
        assertTrue(setChanged);
        assertNotNull(s);
        assertEquals(1, s.size());
      }
    }

    @Nested
    class modifiableSet {
      @Test
      void whenNull_returnsEmptySet() {
        assertAll(
          () -> assertNotNull(Commons.modifiableSet((null))),
          () -> assertTrue(Commons.modifiableSet((null)).isEmpty()));
      }

      @Test
      void canAddElementsInEmptySet() {
        Set<String> nullSet = null;
        Set<String> set = Commons.modifiableSet(nullSet);
        set.add("a");
        assertAll(
          () -> assertEquals(1, set.size()),
          () -> assertFalse(set.isEmpty()),
          () -> assertNotSame(nullSet, set));
      }

      @Test
      void whenUnModifiable_returnModifiable() {
        Set<String> set = new HashSet<>();
        set.add("test1");
        set.add("test2");
        Set<String> unmodifiableSet = Collections.unmodifiableSet(set);
        assertThrows(
          UnsupportedOperationException.class,
          () -> unmodifiableSet.add("shouldNotBeAdded"));

        assertAll(
          () -> assertNotSame(set, Commons.modifiableSet(set)),
          () -> assertNotSame(unmodifiableSet, Commons.modifiableSet(unmodifiableSet)),
          () -> {
            Set<String> retSet = Commons.modifiableSet(unmodifiableSet);
            retSet.add("1");
            assertEquals(3, retSet.size());
          });
      }
    }

    @Nested
    class nullValueThrowIllegalArgument {

      @Test
      void nullTest() {
        assertThrows(
          IllegalArgumentException.class, () -> Commons.nullThrowIllegalArgument(null));
      }

      @Test
      void nullTestMsg() {
        Throwable exception =
          assertThrows(
            IllegalArgumentException.class,
            () -> Commons.nullThrowIllegalArgument(null));
        assertEquals("null not allowed", exception.getMessage());
      }

      @Test
      void nonNullTest() {
        assertDoesNotThrow(() -> Commons.nullThrowIllegalArgument(""));
      }
    }
  }
}