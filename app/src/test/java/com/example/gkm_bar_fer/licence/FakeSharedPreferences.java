package com.example.gkm_bar_fer.licence;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** SharedPreferences en mémoire, pour tester sans téléphone. */
class FakeSharedPreferences implements SharedPreferences {

    private final Map<String, Object> donnees = new HashMap<>();

    /** Mettre à true pour simuler un disque plein / une écriture qui échoue. */
    boolean ecritureEnEchec = false;

    Set<String> cles() {
        return new HashSet<>(donnees.keySet());
    }

    @Override
    public Map<String, ?> getAll() {
        return new HashMap<>(donnees);
    }

    @Override
    public String getString(String key, String defValue) {
        Object v = donnees.get(key);
        return v instanceof String ? (String) v : defValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defValues) {
        Object v = donnees.get(key);
        return v instanceof Set ? (Set<String>) v : defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Object v = donnees.get(key);
        return v instanceof Integer ? (Integer) v : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Object v = donnees.get(key);
        return v instanceof Long ? (Long) v : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Object v = donnees.get(key);
        return v instanceof Float ? (Float) v : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Object v = donnees.get(key);
        return v instanceof Boolean ? (Boolean) v : defValue;
    }

    @Override
    public boolean contains(String key) {
        return donnees.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
    }

    private class FakeEditor implements Editor {
        private final Map<String, Object> modifs = new HashMap<>();
        private boolean toutEffacer = false;

        @Override
        public Editor putString(String key, String value) {
            modifs.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> values) {
            modifs.put(key, values);
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            modifs.put(key, value);
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            modifs.put(key, value);
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            modifs.put(key, value);
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            modifs.put(key, value);
            return this;
        }

        @Override
        public Editor remove(String key) {
            modifs.put(key, null);
            return this;
        }

        @Override
        public Editor clear() {
            toutEffacer = true;
            return this;
        }

        @Override
        public boolean commit() {
            if (ecritureEnEchec) {
                return false;
            }
            appliquer();
            return true;
        }

        @Override
        public void apply() {
            if (!ecritureEnEchec) {
                appliquer();
            }
        }

        private void appliquer() {
            if (toutEffacer) {
                donnees.clear();
            }
            for (Map.Entry<String, Object> e : modifs.entrySet()) {
                if (e.getValue() == null) {
                    donnees.remove(e.getKey());
                } else {
                    donnees.put(e.getKey(), e.getValue());
                }
            }
        }
    }
}