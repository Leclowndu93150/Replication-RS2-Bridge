package com.leclowndu93150.replication_rs2_bridge.block.entity.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.refinedmods.refinedstorage.api.autocrafting.Ingredient;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.PatternLayout;
import com.refinedmods.refinedstorage.api.autocrafting.PatternType;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskSnapshot;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskState;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceListImpl;
import com.refinedmods.refinedstorage.api.resource.list.ResourceList;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Utility for serialising {@link TaskSnapshot} instances to and from NBT.
 */
public final class TaskSnapshotNbt {
    private static final String TAG_ID = "Id";
    private static final String TAG_RESOURCE = "Resource";
    private static final String TAG_AMOUNT = "Amount";
    private static final String TAG_ACTOR = "Actor";
    private static final String TAG_NOTIFY_ACTOR = "NotifyActor";
    private static final String TAG_START_TIME = "StartTime";
    private static final String TAG_INITIAL_REQUIREMENTS = "InitialRequirements";
    private static final String TAG_INTERNAL_STORAGE = "InternalStorage";
    private static final String TAG_CANCELLED = "Cancelled";
    private static final String TAG_STATE = "State";
    private static final String TAG_COMPLETED_PATTERNS = "CompletedPatterns";
    private static final String TAG_PATTERNS = "Patterns";
    private static final String TAG_INGREDIENTS = "Ingredients";
    private static final String TAG_OUTPUTS = "Outputs";
    private static final String TAG_BYPRODUCTS = "Byproducts";
    private static final String TAG_PATTERN = "Pattern";
    private static final String TAG_PATTERN_TYPE = "PatternType";
    private static final String TAG_PATTERN_DATA = "PatternData";
    private static final String TAG_INTERNAL = "Internal";
    private static final String TAG_INTERNAL_PATTERN = "InternalPattern";
    private static final String TAG_EXTERNAL_PATTERN = "ExternalPattern";
    private static final String TAG_ROOT = "Root";
    private static final String TAG_INPUTS = "Inputs";
    private static final String TAG_ORIGINAL_ITERATIONS_REMAINING = "OriginalIterationsRemaining";
    private static final String TAG_ITERATIONS_REMAINING = "IterationsRemaining";
    private static final String TAG_EXPECTED_OUTPUTS = "ExpectedOutputs";
    private static final String TAG_SIMULATED_INPUTS = "SimulatedIterationInputs";
    private static final String TAG_ORIGINAL_ITERATIONS_TO_SEND = "OriginalIterationsToSend";
    private static final String TAG_ITERATIONS_TO_SEND = "IterationsToSend";
    private static final String TAG_ITERATIONS_RECEIVED = "IterationsReceived";
    private static final String TAG_INTERCEPTED = "InterceptedSinceLastStep";
    private static final String TAG_LAST_SINK_RESULT = "LastSinkResult";

    private TaskSnapshotNbt() {
    }

    public static CompoundTag encode(final TaskSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, snapshot.id().id());
        tag.put(TAG_RESOURCE, encodeResourceKey(snapshot.resource()));
        tag.putLong(TAG_AMOUNT, snapshot.amount());
        if (snapshot.actor() instanceof PlayerActor playerActor) {
            tag.putString(TAG_ACTOR, playerActor.name());
        }
        tag.putBoolean(TAG_NOTIFY_ACTOR, snapshot.notifyActor());
        tag.putLong(TAG_START_TIME, snapshot.startTime());
        tag.put(TAG_INITIAL_REQUIREMENTS, encodeResourceList(snapshot.initialRequirements()));
        tag.put(TAG_INTERNAL_STORAGE, encodeResourceList(snapshot.internalStorage()));
        tag.putBoolean(TAG_CANCELLED, snapshot.cancelled());
        tag.putString(TAG_STATE, snapshot.state().name());
        ListTag completed = new ListTag();
        snapshot.completedPatterns().forEach(patternSnapshot -> completed.add(encodePatternSnapshot(patternSnapshot)));
        tag.put(TAG_COMPLETED_PATTERNS, completed);
        ListTag patterns = new ListTag();
        snapshot.patterns().forEach((pattern, patternSnapshot) -> {
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_PATTERN, encodePattern(pattern));
            entry.put(TAG_PATTERN_DATA, encodePatternSnapshot(patternSnapshot));
            patterns.add(entry);
        });
        tag.put(TAG_PATTERNS, patterns);
        return tag;
    }

    public static TaskSnapshot decode(final CompoundTag tag) {
        TaskId id = new TaskId(tag.getUUID(TAG_ID));
        ResourceKey resource = ResourceCodecs.CODEC.parse(NbtOps.INSTANCE, tag.getCompound(TAG_RESOURCE)).result().orElseThrow();
        long amount = tag.getLong(TAG_AMOUNT);
        Actor actor = tag.contains(TAG_ACTOR, Tag.TAG_STRING) ? new PlayerActor(tag.getString(TAG_ACTOR)) : Actor.EMPTY;
        boolean notifyActor = tag.getBoolean(TAG_NOTIFY_ACTOR);
        long startTime = tag.getLong(TAG_START_TIME);
        ResourceList initialRequirements = decodeResourceList(tag.getList(TAG_INITIAL_REQUIREMENTS, Tag.TAG_COMPOUND));
        ResourceList internalStorage = decodeResourceList(tag.getList(TAG_INTERNAL_STORAGE, Tag.TAG_COMPOUND));
        boolean cancelled = tag.getBoolean(TAG_CANCELLED);
        TaskState state = TaskState.valueOf(tag.getString(TAG_STATE));
        List<TaskSnapshot.PatternSnapshot> completed = new ArrayList<>();
        for (Tag completedTag : tag.getList(TAG_COMPLETED_PATTERNS, Tag.TAG_COMPOUND)) {
            completed.add(decodePatternSnapshot((CompoundTag) completedTag));
        }
        Map<Pattern, TaskSnapshot.PatternSnapshot> patterns = new HashMap<>();
        for (Tag patternTag : tag.getList(TAG_PATTERNS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) patternTag;
            Pattern pattern = decodePattern(entry.getCompound(TAG_PATTERN));
            TaskSnapshot.PatternSnapshot snapshot = decodePatternSnapshot(entry.getCompound(TAG_PATTERN_DATA));
            patterns.put(pattern, snapshot);
        }
        return new TaskSnapshot(
            id,
            resource,
            amount,
            actor,
            notifyActor,
            startTime,
            patterns,
            completed,
            initialRequirements,
            internalStorage,
            state,
            cancelled
        );
    }

    private static ListTag encodeResourceList(final ResourceList list) {
        ListTag listTag = new ListTag();
        list.getAll().forEach(resource -> {
            CompoundTag entry = encodeResourceKey(resource);
            entry.putLong(TAG_AMOUNT, list.get(resource));
            listTag.add(entry);
        });
        return listTag;
    }

    private static ResourceList decodeResourceList(final ListTag listTag) {
        MutableResourceList resourceList = MutableResourceListImpl.create();
        for (Tag element : listTag) {
            CompoundTag entry = (CompoundTag) element;
            ResourceKey resource = ResourceCodecs.CODEC.parse(NbtOps.INSTANCE, entry).result().orElseThrow();
            long amount = entry.getLong(TAG_AMOUNT);
            resourceList.add(resource, amount);
        }
        return resourceList;
    }

    private static CompoundTag encodeResourceKey(final ResourceKey resource) {
        if (resource instanceof PlatformResourceKey platformResourceKey) {
            Tag encoded = ResourceCodecs.CODEC.encodeStart(NbtOps.INSTANCE, platformResourceKey)
                .result()
                .orElse(new CompoundTag());
            if (encoded instanceof CompoundTag compoundTag) {
                return compoundTag;
            }
            throw new IllegalStateException("Expected CompoundTag while encoding resource key, got: "
                + encoded.getClass().getSimpleName());
        }
        throw new IllegalStateException("Cannot encode non-platform resource key: " + resource);
    }

    private static CompoundTag encodePatternSnapshot(final TaskSnapshot.PatternSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_ROOT, snapshot.root());
        tag.put(TAG_PATTERN, encodePattern(snapshot.pattern()));
        ListTag ingredients = new ListTag();
        snapshot.ingredients().forEach((slot, inner) -> {
            CompoundTag ingredientTag = new CompoundTag();
            ingredientTag.putInt("Slot", slot);
            ListTag innerList = new ListTag();
            inner.forEach((resource, amount) -> {
                CompoundTag entry = encodeResourceKey(resource);
                entry.putLong(TAG_AMOUNT, amount);
                innerList.add(entry);
            });
            ingredientTag.put(TAG_INPUTS, innerList);
            ingredients.add(ingredientTag);
        });
        tag.put(TAG_INGREDIENTS, ingredients);
        tag.putBoolean(TAG_INTERNAL, snapshot.internalPattern() != null);
        if (snapshot.internalPattern() != null) {
            tag.put(TAG_INTERNAL_PATTERN, encodeInternalPattern(snapshot.internalPattern()));
        } else if (snapshot.externalPattern() != null) {
            tag.put(TAG_EXTERNAL_PATTERN, encodeExternalPattern(snapshot.externalPattern()));
        }
        return tag;
    }

    private static TaskSnapshot.PatternSnapshot decodePatternSnapshot(final CompoundTag tag) {
        boolean root = tag.getBoolean(TAG_ROOT);
        Pattern pattern = decodePattern(tag.getCompound(TAG_PATTERN));
        Map<Integer, Map<ResourceKey, Long>> ingredients = new HashMap<>();
        for (Tag ingredientTag : tag.getList(TAG_INGREDIENTS, Tag.TAG_COMPOUND)) {
            CompoundTag ingredient = (CompoundTag) ingredientTag;
            int slot = ingredient.getInt("Slot");
            Map<ResourceKey, Long> inner = new HashMap<>();
            for (Tag innerTag : ingredient.getList(TAG_INPUTS, Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) innerTag;
                ResourceKey resource = ResourceCodecs.CODEC.parse(NbtOps.INSTANCE, entry).result().orElseThrow();
                inner.put(resource, entry.getLong(TAG_AMOUNT));
            }
            ingredients.put(slot, inner);
        }
        if (tag.getBoolean(TAG_INTERNAL)) {
            TaskSnapshot.InternalPatternSnapshot internal = decodeInternalPattern(tag.getCompound(TAG_INTERNAL_PATTERN));
            return new TaskSnapshot.PatternSnapshot(root, pattern, ingredients, internal, null);
        }
        TaskSnapshot.ExternalPatternSnapshot external = decodeExternalPattern(tag.getCompound(TAG_EXTERNAL_PATTERN));
        return new TaskSnapshot.PatternSnapshot(root, pattern, ingredients, null, external);
    }

    private static CompoundTag encodePattern(final Pattern pattern) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG_ID, pattern.id());
        ListTag ingredients = new ListTag();
        for (Ingredient ingredient : pattern.layout().ingredients()) {
            ingredients.add(encodeIngredient(ingredient));
        }
        tag.put(TAG_INGREDIENTS, ingredients);
        ListTag outputs = new ListTag();
        for (ResourceAmount output : pattern.layout().outputs()) {
            outputs.add(ResourceCodecs.AMOUNT_CODEC.encodeStart(NbtOps.INSTANCE, output).result().orElse(new CompoundTag()));
        }
        tag.put(TAG_OUTPUTS, outputs);
        ListTag byproducts = new ListTag();
        for (ResourceAmount byproduct : pattern.layout().byproducts()) {
            byproducts.add(ResourceCodecs.AMOUNT_CODEC.encodeStart(NbtOps.INSTANCE, byproduct).result().orElse(new CompoundTag()));
        }
        tag.put(TAG_BYPRODUCTS, byproducts);
        tag.putString(TAG_PATTERN_TYPE, pattern.layout().type().name());
        return tag;
    }

    private static Pattern decodePattern(final CompoundTag tag) {
        UUID id = tag.getUUID(TAG_ID);
        List<Ingredient> ingredients = new ArrayList<>();
        for (Tag ingredientTag : tag.getList(TAG_INGREDIENTS, Tag.TAG_COMPOUND)) {
            ingredients.add(decodeIngredient((CompoundTag) ingredientTag));
        }
        List<ResourceAmount> outputs = new ArrayList<>();
        for (Tag outputTag : tag.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND)) {
            outputs.add(ResourceCodecs.AMOUNT_CODEC.parse(NbtOps.INSTANCE, outputTag).result().orElseThrow());
        }
        List<ResourceAmount> byproducts = new ArrayList<>();
        for (Tag byproductTag : tag.getList(TAG_BYPRODUCTS, Tag.TAG_COMPOUND)) {
            byproducts.add(ResourceCodecs.AMOUNT_CODEC.parse(NbtOps.INSTANCE, byproductTag).result().orElseThrow());
        }
        PatternType type = PatternType.valueOf(tag.getString(TAG_PATTERN_TYPE));
        PatternLayout layout = type == PatternType.INTERNAL
            ? PatternLayout.internal(ingredients, outputs, byproducts)
            : PatternLayout.external(ingredients, outputs);
        return new Pattern(id, layout);
    }

    private static CompoundTag encodeIngredient(final Ingredient ingredient) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_AMOUNT, ingredient.amount());
        ListTag inputs = new ListTag();
        ingredient.inputs().forEach(input -> inputs.add(encodeResourceKey(input)));
        tag.put(TAG_INPUTS, inputs);
        return tag;
    }

    private static Ingredient decodeIngredient(final CompoundTag tag) {
        long amount = tag.getLong(TAG_AMOUNT);
        List<ResourceKey> inputs = new ArrayList<>();
        for (Tag inputTag : tag.getList(TAG_INPUTS, Tag.TAG_COMPOUND)) {
            inputs.add(ResourceCodecs.CODEC.parse(NbtOps.INSTANCE, inputTag).result().orElseThrow());
        }
        return new Ingredient(amount, inputs);
    }

    private static CompoundTag encodeInternalPattern(final TaskSnapshot.InternalPatternSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TAG_ORIGINAL_ITERATIONS_REMAINING, snapshot.originalIterationsRemaining());
        tag.putLong(TAG_ITERATIONS_REMAINING, snapshot.iterationsRemaining());
        return tag;
    }

    private static TaskSnapshot.InternalPatternSnapshot decodeInternalPattern(final CompoundTag tag) {
        long original = tag.getLong(TAG_ORIGINAL_ITERATIONS_REMAINING);
        long remaining = tag.getLong(TAG_ITERATIONS_REMAINING);
        return new TaskSnapshot.InternalPatternSnapshot(original, remaining);
    }

    private static CompoundTag encodeExternalPattern(final TaskSnapshot.ExternalPatternSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_EXPECTED_OUTPUTS, encodeResourceList(snapshot.expectedOutputs()));
        tag.put(TAG_SIMULATED_INPUTS, encodeResourceList(snapshot.simulatedIterationInputs()));
        tag.putLong(TAG_ORIGINAL_ITERATIONS_TO_SEND, snapshot.originalIterationsToSendToSink());
        tag.putLong(TAG_ITERATIONS_TO_SEND, snapshot.iterationsToSendToSink());
        tag.putLong(TAG_ITERATIONS_RECEIVED, snapshot.iterationsReceived());
        tag.putBoolean(TAG_INTERCEPTED, snapshot.interceptedAnythingSinceLastStep());
        if (snapshot.lastSinkResult() != null) {
            tag.putString(TAG_LAST_SINK_RESULT, snapshot.lastSinkResult().name());
        }
        return tag;
    }

    private static TaskSnapshot.ExternalPatternSnapshot decodeExternalPattern(final CompoundTag tag) {
        ResourceList expectedOutputs = decodeResourceList(tag.getList(TAG_EXPECTED_OUTPUTS, Tag.TAG_COMPOUND));
        ResourceList simulatedInputs = decodeResourceList(tag.getList(TAG_SIMULATED_INPUTS, Tag.TAG_COMPOUND));
        long originalIterationsToSend = tag.getLong(TAG_ORIGINAL_ITERATIONS_TO_SEND);
        long iterationsToSend = tag.getLong(TAG_ITERATIONS_TO_SEND);
        long iterationsReceived = tag.getLong(TAG_ITERATIONS_RECEIVED);
        boolean intercepted = tag.getBoolean(TAG_INTERCEPTED);
        ExternalPatternSink.Result sinkResult = tag.contains(TAG_LAST_SINK_RESULT)
            ? ExternalPatternSink.Result.valueOf(tag.getString(TAG_LAST_SINK_RESULT))
            : null;
        return new TaskSnapshot.ExternalPatternSnapshot(
            expectedOutputs,
            simulatedInputs,
            originalIterationsToSend,
            iterationsToSend,
            iterationsReceived,
            intercepted,
            sinkResult,
            null
        );
    }
}
