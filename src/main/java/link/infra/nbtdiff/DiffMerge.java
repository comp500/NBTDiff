package link.infra.nbtdiff;

import net.minecraft.nbt.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DiffMerge {
	static class RemovalTracker {
		private final CompoundTag root;
		private final Deque<String> path = new ArrayDeque<>();

		public RemovalTracker(CompoundTag root) {
			this.root = root;
		}

		public void push(String pathElement) {
			path.push(pathElement);
		}

		public void pop() {
			path.pop();
		}

		private final Deque<String> tempPathTraversal = new ArrayDeque<>();

		public void remove() {
			tempPathTraversal.addAll(path);
			removeRecursive(root);
			tempPathTraversal.clear();
		}

		private void removeRecursive(CompoundTag tag) {
			String key = tempPathTraversal.removeLast();
			Tag value = tag.get(key);
			if (value instanceof CompoundTag) {
				if (!tempPathTraversal.isEmpty()) {
					removeRecursive((CompoundTag) value);
				}
			} else {
				CompoundTag newTag = new CompoundTag();
				tag.put(key, newTag);
				if (!tempPathTraversal.isEmpty()) {
					removeRecursive(newTag);
				}
			}
		}

		public boolean isRemoved() {
			tempPathTraversal.addAll(path);
			boolean res;
			res = isRemovedRecursive(root);
			tempPathTraversal.clear();
			return res;
		}

		private boolean isRemovedRecursive(CompoundTag tag) {
			String key = tempPathTraversal.removeLast();
			if (tag.contains(key)) {
				if (tempPathTraversal.isEmpty()) {
					return true;
				}
				return isRemovedRecursive((CompoundTag) Objects.requireNonNull(tag.get(key)));
			}
			return false;
		}
	}

	static Tag diff(Tag oldTag, Tag newTag, RemovalTracker tracker) {
		if (newTag == null) {
			if (oldTag != null) {
				tracker.remove();
			}
			return null;
		}
		if (oldTag == null) {
			return newTag;
		}
		if (newTag.equals(oldTag)) {
			return null;
		}
		if (oldTag instanceof CompoundTag && newTag instanceof CompoundTag) {
			return diffCompoundTag((CompoundTag) oldTag, (CompoundTag) newTag, tracker);
		}
		if (oldTag instanceof ByteArrayTag && newTag instanceof ByteArrayTag) {
			return diffByteArrayTag((ByteArrayTag) oldTag, (ByteArrayTag) newTag);
		}
		if (oldTag instanceof IntArrayTag && newTag instanceof IntArrayTag) {
			return diffIntArrayTag((IntArrayTag) oldTag, (IntArrayTag) newTag);
		}
		if (oldTag instanceof LongArrayTag && newTag instanceof LongArrayTag) {
			return diffLongArrayTag((LongArrayTag) oldTag, (LongArrayTag) newTag);
		}
		if (oldTag instanceof ListTag && newTag instanceof ListTag) {
			return diffListTag((ListTag) oldTag, (ListTag) newTag, tracker);
		}
		return newTag;
	}

	@NotNull
	private static Tag diffCompoundTag(CompoundTag oldTag, CompoundTag newTag, RemovalTracker tracker) {
		Set<String> allKeys = new HashSet<>();
		allKeys.addAll(oldTag.getKeys());
		allKeys.addAll(newTag.getKeys());
		CompoundTag diff = new CompoundTag();
		for (String key : allKeys) {
			tracker.push(key);
			Tag newValue = diff(oldTag.get(key), newTag.get(key), tracker);
			if (newValue != null) {
				diff.put(key, newValue);
			}
			tracker.pop();
		}
		return diff;
	}

	@NotNull
	private static Tag diffByteArrayTag(ByteArrayTag oldTag, ByteArrayTag newTag) {
		// Diff by XOR
		byte[] oldBytes = oldTag.getByteArray();
		byte[] newBytes = newTag.getByteArray();
		byte[] diff = new byte[newBytes.length];
		for (int i = 0; i < diff.length; i++) {
			if (i < oldBytes.length) {
				diff[i] = (byte) (oldBytes[i] ^ newBytes[i]);
			} else {
				diff[i] = newBytes[i];
			}
		}
		return new ByteArrayTag(diff);
	}

	@NotNull
	private static Tag diffIntArrayTag(IntArrayTag oldTag, IntArrayTag newTag) {
		int[] oldInts = oldTag.getIntArray();
		int[] newInts = newTag.getIntArray();
		int[] diff = new int[newInts.length];
		for (int i = 0; i < diff.length; i++) {
			if (i < oldInts.length) {
				diff[i] = oldInts[i] ^ newInts[i];
			} else {
				diff[i] = newInts[i];
			}
		}
		return new IntArrayTag(diff);
	}

	@NotNull
	private static Tag diffLongArrayTag(LongArrayTag oldTag, LongArrayTag newTag) {
		long[] oldLongs = oldTag.getLongArray();
		long[] newLongs = newTag.getLongArray();
		long[] diff = new long[newLongs.length];
		for (int i = 0; i < diff.length; i++) {
			if (i < oldLongs.length) {
				diff[i] = oldLongs[i] ^ newLongs[i];
			} else {
				diff[i] = newLongs[i];
			}
		}
		return new LongArrayTag(diff);
	}

	@NotNull
	private static Tag diffListTag(ListTag oldTag, ListTag newTag, RemovalTracker tracker) {
		ListTag diffTag = new ListTag();
		for (int i = 0; i < newTag.size(); i++) {
			CompoundTag diffElement = new CompoundTag();
			// Attempt to locate an existing element with the same value
			boolean found = false;
			for (int j = 0; j < oldTag.size(); j++) {
				if (newTag.get(i).equals(oldTag.get(j))) {
					diffElement.putInt("r", j);
					found = true;
					break;
				}
			}
			if (!found) {
				// Diff with the same index
				if (i < oldTag.size()) {
					diffElement.putInt("r", i);
					tracker.push(Integer.toString(i));
					Tag diffValue = diff(oldTag.get(i), newTag.get(i), tracker);
					if (diffValue != null) {
						diffElement.put("d", diffValue);
					}
					tracker.pop();
				} else {
					// Just use new value if there is no corresponding value in the old list
					diffElement.put("n", newTag.get(i));
				}
			}
			diffTag.add(i, diffElement);
		}
		return diffTag;
	}

	@NotNull
	private static Tag mergeListTag(ListTag oldTag, ListTag diffTag, RemovalTracker tracker) {
		ListTag mergedTag = new ListTag();
		for (int i = 0; i < diffTag.size(); i++) {
			CompoundTag diffElement = diffTag.getCompound(i);
			if (diffElement.contains("n")) {
				mergedTag.add(i, diffElement.get("n"));
			} else if (diffElement.contains("r")) {
				int reference = diffElement.getInt("r");
				Tag oldElement = oldTag.get(reference);
				if (diffElement.contains("d")) {
					tracker.push(Integer.toString(reference));
					mergedTag.add(i, merge(oldElement, diffElement.get("d"), tracker));
					tracker.pop();
				} else {
					mergedTag.add(i, oldElement);
				}
			}
		}
		return mergedTag;
	}

	static Tag merge(Tag oldTag, Tag diffTag, RemovalTracker tracker) {
		if (diffTag == null) {
			if (tracker.isRemoved()) {
				return null;
			} else {
				return oldTag;
			}
		}
		if (oldTag == null) {
			return diffTag;
		}
		if (oldTag instanceof CompoundTag && diffTag instanceof CompoundTag) {
			return mergeCompoundTag((CompoundTag) oldTag, (CompoundTag) diffTag, tracker);
		}
		if (oldTag instanceof ByteArrayTag && diffTag instanceof ByteArrayTag) {
			return diffByteArrayTag((ByteArrayTag) oldTag, (ByteArrayTag) diffTag);
		}
		if (oldTag instanceof IntArrayTag && diffTag instanceof IntArrayTag) {
			return diffIntArrayTag((IntArrayTag) oldTag, (IntArrayTag) diffTag);
		}
		if (oldTag instanceof LongArrayTag && diffTag instanceof LongArrayTag) {
			return diffLongArrayTag((LongArrayTag) oldTag, (LongArrayTag) diffTag);
		}
		if (oldTag instanceof ListTag && diffTag instanceof ListTag) {
			return mergeListTag((ListTag) oldTag, (ListTag) diffTag, tracker);
		}
		return diffTag;
	}

	@NotNull
	private static Tag mergeCompoundTag(CompoundTag oldTag, CompoundTag diffTag, RemovalTracker tracker) {
		Set<String> allKeys = new HashSet<>();
		allKeys.addAll(oldTag.getKeys());
		allKeys.addAll(diffTag.getKeys());
		CompoundTag merged = new CompoundTag();
		for (String key : allKeys) {
			tracker.push(key);
			Tag newValue = merge(oldTag.get(key), diffTag.get(key), tracker);
			if (newValue != null) {
				merged.put(key, newValue);
			}
			tracker.pop();
		}
		return merged;
	}
}
