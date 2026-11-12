package emul.java.util.concurrent;

import java.util.ArrayDeque;

/** Single-threaded TeaVM: threads switch only where they block, so a plain deque is safe. */
public class TConcurrentLinkedQueue<E> extends ArrayDeque<E> {
}
