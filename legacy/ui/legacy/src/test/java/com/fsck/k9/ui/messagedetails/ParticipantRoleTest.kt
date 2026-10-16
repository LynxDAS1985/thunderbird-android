package com.fsck.k9.ui.messagedetails

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class ParticipantRoleTest {
    @Test
    fun `only author roles support sender brand`() {
        assertThat(ParticipantRole.FROM.supportsSenderBrand).isTrue()
        assertThat(ParticipantRole.SENDER.supportsSenderBrand).isTrue()
        assertThat(ParticipantRole.REPLY_TO.supportsSenderBrand).isFalse()
        assertThat(ParticipantRole.TO.supportsSenderBrand).isFalse()
        assertThat(ParticipantRole.CC.supportsSenderBrand).isFalse()
        assertThat(ParticipantRole.BCC.supportsSenderBrand).isFalse()
    }
}
