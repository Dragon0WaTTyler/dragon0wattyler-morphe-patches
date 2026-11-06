package app.spicetify.patches.spotify.settings

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeFailureObserverTest {
    private fun site(register: Int = 3, owner: String = "Lp/q721;") = listOf(
        ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0,
            ImmutableMethodReference(owner, "a", listOf("Ljava/lang/Object;"), "Ljava/lang/Throwable;")),
        ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, register),
        ImmutableInstruction21t(Opcode.IF_EQZ, 3, 2),
    )

    @Test fun `observer is inserted after the native result and before its null test`() {
        assertEquals(2, homeFailureObserverSite(site()))
    }

    @Test fun `changed register owner and ambiguous results are refused`() {
        assertNull(homeFailureObserverSite(site(register = 4)))
        assertNull(homeFailureObserverSite(site(owner = "Lp/other;")))
        assertNull(homeFailureObserverSite(site() + site()))
        assertNull(homeFailureObserverSite(emptyList()))
    }
}
