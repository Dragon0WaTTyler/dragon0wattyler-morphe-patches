/* SPDX-License-Identifier: GPL-3.0-only
 * Independent Subscription Groups groups.6, modified 2026-11-02. See LICENSE/NOTICE.
 */
package app.morphe.extension.youtube.groups;

interface WatchedState {
    boolean ready();
    boolean contains(String id);
    void mark(String id, boolean watched);
    void clear();
    void observe(Runnable listener);
    void remove(Runnable listener);
    WatchedState EMPTY = new WatchedState() {
        public boolean ready() { return false; }
        public boolean contains(String id) { return false; }
        public void mark(String id, boolean watched) { }
        public void clear() { }
        public void observe(Runnable listener) { }
        public void remove(Runnable listener) { }
    };
}
