package net.vulkanic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Validates and normalizes {@link VulkanicPassResourceModel.PassRequest}
 * instances before a backend derives native layout, barrier, descriptor, and
 * render-target plans from them.
 */
public final class VulkanicPassResourcePlanner {
    private VulkanicPassResourcePlanner() {
    }

    public static VulkanicPassResourceModel.PassExecutionPlan plan(
        VulkanicPassResourceModel.PassRequest request
    ) {
        Objects.requireNonNull(request, "request");
        List<VulkanicPassResourceModel.ResourceUse> orderedUses = new ArrayList<>();
        int attachmentOrder = 0;
        for (VulkanicPassResourceModel.AttachmentUse attachment : request.attachments()) {
            orderedUses.add(attachment.passResourceUse(attachmentOrder++));
        }
        orderedUses.addAll(request.resources());
        for (VulkanicPassResourceModel.BindingSnapshot binding : request.bindings()) {
            orderedUses.add(binding.resourceUse());
        }
        orderedUses.sort(Comparator.comparingInt(VulkanicPassResourceModel.ResourceUse::order));

        validateConflicts(orderedUses);

        List<VulkanicPassResourceModel.ResourceUse> finalUsages = orderedUses.stream()
            .filter(use -> use.usage() != VulkanicResourceUsage.INFERRED)
            .toList();
        return new VulkanicPassResourceModel.PassExecutionPlan(request, orderedUses, finalUsages);
    }

    private static void validateConflicts(List<VulkanicPassResourceModel.ResourceUse> orderedUses) {
        for (int leftIndex = 0; leftIndex < orderedUses.size(); leftIndex++) {
            VulkanicPassResourceModel.ResourceUse left = orderedUses.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < orderedUses.size(); rightIndex++) {
                VulkanicPassResourceModel.ResourceUse right = orderedUses.get(rightIndex);
                if (!sameResource(left, right) || !left.subresource().overlaps(right.subresource())) {
                    continue;
                }
                if (!conflictingAccess(left, right)) {
                    continue;
                }
                if (declaredFeedbackLoop(left, right)) {
                    continue;
                }
                throw new IllegalArgumentException(
                    "Conflicting resource usage in pass: "
                        + left.resource().stableKey()
                        + " roles "
                        + left.role()
                        + " and "
                        + right.role()
                        + " access "
                        + left.access()
                        + "/"
                        + right.access()
                );
            }
        }
    }

    private static boolean sameResource(
        VulkanicPassResourceModel.ResourceUse left,
        VulkanicPassResourceModel.ResourceUse right
    ) {
        return left.resource().stableKey().equals(right.resource().stableKey());
    }

    private static boolean conflictingAccess(
        VulkanicPassResourceModel.ResourceUse left,
        VulkanicPassResourceModel.ResourceUse right
    ) {
        return left.writes() || right.writes();
    }

    private static boolean declaredFeedbackLoop(
        VulkanicPassResourceModel.ResourceUse left,
        VulkanicPassResourceModel.ResourceUse right
    ) {
        return left.feedbackLoop()
            && right.feedbackLoop()
            && left.resource().kind() == VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT
            && right.resource().kind() == VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT;
    }
}
