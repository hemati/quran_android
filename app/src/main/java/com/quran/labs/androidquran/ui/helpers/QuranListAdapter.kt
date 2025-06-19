package com.quran.labs.androidquran.ui.helpers

import android.content.Context
import android.graphics.PorterDuff
import android.util.SparseBooleanArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.util.set
import androidx.recyclerview.widget.RecyclerView
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.JuzView
import com.quran.labs.androidquran.view.TagsViewGroup
import java.text.SimpleDateFormat
import java.util.Date

class QuranListAdapter(
  private val context: Context,
  private val recyclerView: RecyclerView,
  private var elements: Array<QuranRow>,
  private val isEditable: Boolean
) : RecyclerView.Adapter<QuranListAdapter.HeaderHolder>(),
  View.OnClickListener, View.OnLongClickListener {

  private val inflater = LayoutInflater.from(context)
  private val checkedState = SparseBooleanArray()
  private val locale = QuranUtils.getCurrentLocale()
  private var tagMap: Map<Long, Tag> = emptyMap()
  private var showTags = false
  private var showDate = false

  private var touchListener: QuranTouchListener? = null

  private val surahUnicodes = mapOf(
    1 to "\uE904",
    2 to "\uE905",
    3 to "\uE906",
    4 to "\uE907",
    5 to "\uE908",
    6 to "\uE90B",
    7 to "\uE90C",
    8 to "\uE90D",
    9 to "\uE90E",
    10 to "\uE90F",
    11 to "\uE910",
    12 to "\uE911",
    13 to "\uE912",
    14 to "\uE913",
    15 to "\uE914",
    16 to "\uE915",
    17 to "\uE916",
    18 to "\uE917",
    19 to "\uE918",
    20 to "\uE919",
    21 to "\uE91A",
    22 to "\uE91B",
    23 to "\uE91C",
    24 to "\uE91D",
    25 to "\uE91E",
    26 to "\uE91F",
    27 to "\uE920",
    28 to "\uE921",
    29 to "\uE922",
    30 to "\uE923",
    31 to "\uE924",
    32 to "\uE925",
    33 to "\uE926",
    34 to "\uE92E",
    35 to "\uE92F",
    36 to "\uE930",
    37 to "\uE931",
    38 to "\uE909",
    39 to "\uE90A",
    40 to "\uE927",
    41 to "\uE928",
    42 to "\uE929",
    43 to "\uE92A",
    44 to "\uE92B",
    45 to "\uE92C",
    46 to "\uE92D",
    47 to "\uE932",
    48 to "\uE902",
    49 to "\uE933",
    50 to "\uE934",
    51 to "\uE935",
    52 to "\uE936",
    53 to "\uE937",
    54 to "\uE938",
    55 to "\uE939",
    56 to "\uE93A",
    57 to "\uE93B",
    58 to "\uE93C",
    59 to "\uE900",
    60 to "\uE901",
    61 to "\uE941",
    62 to "\uE942",
    63 to "\uE943",
    64 to "\uE944",
    65 to "\uE945",
    66 to "\uE946",
    67 to "\uE947",
    68 to "\uE948",
    69 to "\uE949",
    70 to "\uE94A",
    71 to "\uE94B",
    72 to "\uE94C",
    73 to "\uE94D",
    74 to "\uE94E",
    75 to "\uE94F",
    76 to "\uE950",
    77 to "\uE951",
    78 to "\uE952",
    79 to "\uE93D",
    80 to "\uE93E",
    81 to "\uE93F",
    82 to "\uE940",
    83 to "\uE953",
    84 to "\uE954",
    85 to "\uE955",
    86 to "\uE956",
    87 to "\uE957",
    88 to "\uE958",
    89 to "\uE959",
    90 to "\uE95A",
    91 to "\uE95B",
    92 to "\uE95C",
    93 to "\uE95D",
    94 to "\uE95E",
    95 to "\uE95F",
    96 to "\uE960",
    97 to "\uE961",
    98 to "\uE962",
    99 to "\uE963",
    100 to "\uE964",
    101 to "\uE965",
    102 to "\uE966",
    103 to "\uE967",
    104 to "\uE968",
    105 to "\uE969",
    106 to "\uE96A",
    107 to "\uE96B",
    108 to "\uE96C",
    109 to "\uE96D",
    110 to "\uE96E",
    111 to "\uE96F",
    112 to "\uE970",
    113 to "\uE971",
    114 to "\uE972"
  )


  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderHolder {
    return if (viewType == 0) {
      HeaderHolder(inflater.inflate(R.layout.index_header_row, parent, false))
    } else {
      ViewHolder(inflater.inflate(R.layout.index_sura_row, parent, false))
    }
  }

  override fun onBindViewHolder(holder: HeaderHolder, position: Int) {
    val type = getItemViewType(position)
    return if (type == 0) bindHeader(holder, position) else bindRow(holder, position)
  }

  override fun getItemCount(): Int = elements.size

  override fun getItemId(position: Int): Long = position.toLong()

  override fun getItemViewType(position: Int): Int {
    return if (elements[position].isHeader) 0 else 1
  }

  override fun onClick(v: View) {
    val position = recyclerView.getChildAdapterPosition(v)
    if (position != RecyclerView.NO_POSITION) {
      val element = elements[position]
      if (touchListener == null) {
        (context as QuranActivity).jumpTo(element.page)
      } else {
        touchListener?.onClick(element, position)
      }
    }
  }

  override fun onLongClick(v: View): Boolean {
    touchListener?.let { listener ->
      val position = recyclerView.getChildAdapterPosition(v)
      if (position != RecyclerView.NO_POSITION) {
        return listener.onLongClick(elements[position], position)
      }
    }
    return false
  }

  fun setElements(elements: Array<QuranRow>) {
    this.elements = elements
    notifyDataSetChanged()
  }

  fun isItemChecked(position: Int): Boolean = checkedState[position]

  fun setItemChecked(position: Int, checked: Boolean) {
    checkedState[position] = checked
    notifyItemChanged(position)
  }

  fun uncheckAll() {
    checkedState.clear()
    notifyDataSetChanged()
  }

  fun getCheckedItems(): List<QuranRow> {
    val result = ArrayList<QuranRow>()
    val count = checkedState.size()
    val elements = itemCount
    for (i in 0 until count) {
      val key = checkedState.keyAt(i)
      // TODO: figure out why sometimes elements > key
      if (checkedState[key] && elements > key) {
        result.add(getQuranRow(key))
      }
    }
    return result
  }

  fun setQuranTouchListener(listener: QuranTouchListener) {
    touchListener = listener
  }

  fun setElements(elements: Array<QuranRow>, tagMap: Map<Long, Tag>) {
    this.elements = elements
    this.tagMap = tagMap
  }

  fun setShowTags(showTags: Boolean) {
    this.showTags = showTags
  }

  fun setShowDate(showDate: Boolean) {
    this.showDate = showDate
  }

  private fun getQuranRow(position: Int): QuranRow = elements[position]

  private fun bindRow(vh: HeaderHolder, position: Int) {
    val holder = vh as ViewHolder
    bindHeader(vh, position)
    val item = elements[position]

    with(holder) {
      number.text = QuranUtils.getLocalizedNumber(item.sura)
      metadata.visibility = View.VISIBLE
      metadata.text = item.metadata
      tags.visibility = View.GONE

      suraArabic.text = surahUnicodes[item.sura]

      when {
        item.juzType != null -> {
          image.setImageDrawable(
            JuzView(context, item.juzType, item.juzOverlayText)
          )
          image.visibility = View.VISIBLE
          number.visibility = View.GONE
        }
        item.imageResource == null -> {
          number.visibility = View.VISIBLE
          image.visibility = View.GONE
        }
        else -> {
          image.setImageResource(item.imageResource)
          if (item.imageFilterColorResource == null) {
            image.colorFilter = null
          } else {
            image.setColorFilter(
              ContextCompat.getColor(context, item.imageFilterColorResource), PorterDuff.Mode.SRC_ATOP
            )
          }

          if (showDate) {
            val date = SimpleDateFormat("MMM dd, HH:mm", locale)
              .format(Date(item.dateAddedInMillis))
            holder.metadata.text = buildString {
              append(item.metadata)
              append(" - ")
              append(date)
            }
          }

          image.visibility = View.VISIBLE
          number.visibility = View.GONE

          val tagList = ArrayList<Tag>()
          val bookmark = item.bookmark
          if (bookmark != null && bookmark.tags.isNotEmpty() && showTags) {
            for (i in 0 until bookmark.tags.size) {
              val tagId = bookmark.tags[i]
              val tag = tagMap[tagId]
              tag?.let { tagList.add(it) }
            }
          }

          if (tagList.isEmpty()) {
            tags.visibility = View.GONE
          } else {
            tags.setTags(tagList)
            tags.visibility = View.VISIBLE
          }
        }
      }
    }
  }

  private fun bindHeader(holder: HeaderHolder, pos: Int) {
    val item = elements[pos]
    holder.title.text = item.text
    if (item.page == 0) {
      holder.pageNumber.visibility = View.GONE
    } else {
      holder.pageNumber.visibility = View.VISIBLE
      holder.pageNumber.text = QuranUtils.getLocalizedNumber(item.page)
    }
    holder.setChecked(isItemChecked(pos))
    holder.setEnabled(isEnabled(pos))
  }

  private fun isEnabled(position: Int): Boolean {
    val selected = elements[position]
    return !isEditable ||                     // anything in surahs or juzs
        selected.isBookmark ||                // actual bookmarks
        selected.rowType == QuranRow.NONE ||  // the actual "current page"
        selected.isBookmarkHeader             // tags
  }

  open inner class HeaderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val view: View = itemView
    val title: TextView = itemView.findViewById(R.id.title)
    val pageNumber: TextView = itemView.findViewById(R.id.pageNumber)

    fun setEnabled(enabled: Boolean) {
      view.isEnabled = true
      itemView.setOnClickListener(
        if (enabled) this@QuranListAdapter else null
      )
      itemView.setOnLongClickListener(
        if (isEditable && enabled) this@QuranListAdapter else null
      )
    }

    fun setChecked(checked: Boolean) {
      view.isActivated = checked
    }
  }

  private inner class ViewHolder(itemView: View) : HeaderHolder(itemView) {
    val metadata: TextView = itemView.findViewById(R.id.metadata)
    val number: TextView = itemView.findViewById(R.id.suraNumber)
    val image: ImageView = itemView.findViewById(R.id.rowIcon)
    val tags: TagsViewGroup = itemView.findViewById(R.id.tags)
    val date: TextView? = itemView.findViewById(R.id.show_date)
    val suraArabic: TextView = itemView.findViewById(R.id.sura_name_arabic)

  }

  interface QuranTouchListener {
    fun onClick(row: QuranRow, position: Int)
    fun onLongClick(row: QuranRow, position: Int): Boolean
  }
}
