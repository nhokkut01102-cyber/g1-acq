package com.hanbubble

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hanbubble.data.Entry
import com.hanbubble.data.HistoryStore
import com.hanbubble.databinding.ActivityHistoryBinding
import com.hanbubble.databinding.ItemHistoryBinding
import com.hanbubble.ui.KoreanView
import com.hanbubble.ui.Speaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Sổ học: đọc lại mọi câu đã viết khi bật Hàn Bubble, có tô màu từ loại và ngữ pháp. */
class HistoryActivity : AppCompatActivity() {

    private lateinit var b: ActivityHistoryBinding
    private lateinit var prefs: Prefs
    private lateinit var speaker: Speaker
    private val adapter = EntryAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(b.root)
        ViewCompat.setOnApplyWindowInsetsListener(b.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        prefs = Prefs(this)
        speaker = Speaker(this)

        b.btnBack.setOnClickListener { finish() }
        b.btnClearAll.setOnClickListener { confirmClearAll() }
        KoreanView.fillLegend(b.legend)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    private fun reload() {
        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { HistoryStore.get(this@HistoryActivity).list() }
            adapter.submit(entries)
            b.count.text = "${entries.size} câu"
            b.empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_all_title)
            .setMessage(R.string.clear_all_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { HistoryStore.get(this@HistoryActivity).clear() }
                    reload()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun delete(entry: Entry) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { HistoryStore.get(this@HistoryActivity).delete(entry.id) }
            Toast.makeText(this@HistoryActivity, "Đã xoá", Toast.LENGTH_SHORT).show()
            reload()
        }
    }

    private inner class EntryAdapter : RecyclerView.Adapter<EntryAdapter.Holder>() {
        private var items: List<Entry> = emptyList()

        inner class Holder(val v: ItemHistoryBinding) : RecyclerView.ViewHolder(v.root)

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<Entry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val e = items[position]
            val time = TIME_FMT.format(Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()))
            holder.v.meta.text = listOfNotNull(time, e.app).joinToString(" · ")
            KoreanView.bind(holder.v.result, e.result, prefs.showRoman, expanded = false)
            holder.v.btnSpeak.setOnClickListener { speaker.speak(e.result.korean) }
            holder.v.result.koreanText.setOnClickListener { speaker.speak(e.result.korean) }
            holder.v.btnDelete.setOnClickListener { delete(e) }
        }
    }

    private companion object {
        val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm · dd/MM/yyyy")
    }
}
