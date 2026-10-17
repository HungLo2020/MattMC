package net.vulkanic.world;

/** Position in a membership journal; a reset expires marks from its old epoch. */
record MembershipJournalMark(long epoch, int position) {}
