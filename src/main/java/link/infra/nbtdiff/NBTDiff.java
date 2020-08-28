package link.infra.nbtdiff;

import net.fabricmc.api.ModInitializer;
import net.minecraft.nbt.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionFile;

import java.io.*;
import java.util.*;

public class NBTDiff implements ModInitializer {
	@Override
	public void onInitialize() {
//		CompoundTag first = new CompoundTag();
//		first.putString("hello", "first");
//		first.putString("shouldbe", "removed");
//		first.putByteArray("bytes", "hello".getBytes());
//		CompoundTag second = first.copy();
//		second.remove("shouldbe");
//		second.putString("bye", "second");
//		second.putByteArray("bytes", "hell yeah".getBytes());
//		CompoundTag remove = new CompoundTag();
//		RemovalTracker tracker = new RemovalTracker(remove);
//		System.out.println("Original: " + first);
//		System.out.println("New: " + second);
//		System.out.println("Diff: " + diff(first, second, tracker));
//		System.out.println("Remove: " + remove);
//		tracker = new RemovalTracker(new CompoundTag());
//		System.out.println("Merged: " + merge(first, diff(first, second, tracker), tracker));
//		tracker = new RemovalTracker(new CompoundTag());
//		System.out.println("Equal: " + Objects.equals(merge(first, diff(first, second, tracker), tracker), second));

		/*
			Wow, you found my diffing code!!
			What you could do to make it better:
				Not use region files (so no padding between chunks)
				Compress all chunks at once
				Use a binary diff algorithm (like bsdiff) to diff primitive array data
		 */

		System.out.println("Starting diff...");

		try {
			RegionFile oldFile = new RegionFile(new File("testworkarea/r.-1.-1.old.mca"), new File("testworkarea"), false);
			RegionFile newFile = new RegionFile(new File("testworkarea/r.-1.-1.new.mca"), new File("testworkarea"), false);
			RegionFile diffFile = new RegionFile(new File("testworkarea/r.-1.-1.diff.mca"), new File("testworkarea"), false);

			for (int i = 0; i < 32; i++) {
				for (int j = 0; j < 32; j++) {
					ChunkPos pos = new ChunkPos(i + (-1 << 5), j + (-1 << 5));
					if (newFile.hasChunk(pos)) {
						if (oldFile.hasChunk(pos)) {
							// Read NBT from old and new
							try (DataInputStream oldStream = oldFile.getChunkInputStream(pos);
								 DataInputStream newStream = newFile.getChunkInputStream(pos);
								 DataOutputStream diffStream = diffFile.getChunkOutputStream(pos)) {
								if (oldStream == null || newStream == null) {
									// TODO: error?
									continue;
								}
								CompoundTag diffStore = new CompoundTag();
								CompoundTag removals = new CompoundTag();
								DiffMerge.RemovalTracker tracker = new DiffMerge.RemovalTracker(removals);

								CompoundTag oldTag = NbtIo.read(oldStream);
								CompoundTag newTag = NbtIo.read(newStream);
								Tag diffTag = DiffMerge.diff(oldTag, newTag, tracker);
								if (diffTag == null) {
									continue;
								}

								diffStore.put("r", removals);
								diffStore.put("d", diffTag);
								NbtIo.write(diffStore, diffStream);

								if (!Objects.equals(newTag, DiffMerge.merge(oldTag, diffTag, tracker))) {
									// TODO: explode
									System.out.println("oh no");
								}
							}
						} else {
							// Copy NBT from new into diff chunk
							try (DataInputStream newStream = newFile.getChunkInputStream(pos);
								 DataOutputStream diffStream = diffFile.getChunkOutputStream(pos)) {
								if (newStream == null) {
									// TODO: error?
									continue;
								}
								CompoundTag diffStore = new CompoundTag();
								CompoundTag newTag = NbtIo.read(newStream);
								diffStore.put("n", newTag);
								NbtIo.write(diffStore, diffStream);
							}
						}
					}
				}
			}

			oldFile.close();
			newFile.close();
			diffFile.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Diff done!");

	}

}
