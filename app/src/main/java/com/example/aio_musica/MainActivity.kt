package com.example.aio_musica

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.aio_musica.ui.FileScreen
import com.example.aio_musica.ui.MusicScreen
import com.example.aio_musica.ui.PlaylistScreen
import com.example.aio_musica.ui.LibraryScreen
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0
            )
            insets
        }
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            val selectedScreen: Fragment = when (item.itemId) {
                R.id.nav_file -> FileScreen()
                R.id.nav_music -> MusicScreen()
                R.id.nav_playlist -> PlaylistScreen()
                R.id.nav_library -> LibraryScreen()
                else -> return@setOnItemSelectedListener false
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, selectedScreen)
                .commit()
            true
        }
        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_file
        }
    }
}
