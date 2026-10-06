package com.focuslamp.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.focuslamp.app.Graph
import com.focuslamp.app.R
import com.focuslamp.app.databinding.ActivityWelcomeBinding
import com.focuslamp.app.ui.parent.ParentActivity
import com.focuslamp.app.util.Role

/** First launch: whose phone is this? Afterwards it just routes to the right home. */
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (Graph.settings.role) {
            Role.STUDENT -> return go(MainActivity::class.java)
            Role.PARENT -> return go(ParentActivity::class.java)
            null -> Unit
        }
        val b = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.glow.setLampColor(getColor(R.color.lamp_green), animate = false)
        b.nameInput.setText(Graph.settings.displayName)

        fun choose(role: Role) {
            val name = b.nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                b.nameLayout.error = "Add a name so the other phone knows who this is."
                return
            }
            Graph.settings.displayName = name
            Graph.settings.role = role
            go(if (role == Role.STUDENT) MainActivity::class.java else ParentActivity::class.java)
        }
        b.student.setOnClickListener { choose(Role.STUDENT) }
        b.parent.setOnClickListener { choose(Role.PARENT) }
    }

    private fun go(cls: Class<*>) {
        startActivity(Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
