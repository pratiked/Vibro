package demo.pratiked.vibro.holders

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.TextView
import demo.pratiked.vibro.R

class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val txtTitle: TextView = itemView.findViewById(R.id.txt_title)
    private val txtArtist: TextView = itemView.findViewById(R.id.txt_artist)
    private val txtSize: TextView = itemView.findViewById(R.id.txt_size)
    private val txtDuration: TextView = itemView.findViewById(R.id.txt_duration)

    fun setTitle(title: String) {
        txtTitle.text = title
    }

    fun setArtist(artist: String) {
        txtArtist.text = artist
    }

    fun setSize(size: String) {
        txtSize.text = size
    }

    fun setDuration(duration: String) {
        txtDuration.text = duration
    }
}
