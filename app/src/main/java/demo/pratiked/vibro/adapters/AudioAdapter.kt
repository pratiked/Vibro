package demo.pratiked.vibro.adapters

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import demo.pratiked.vibro.R
import demo.pratiked.vibro.holders.AudioViewHolder
import demo.pratiked.vibro.models.Audio

class AudioAdapter(private val mAudioList: ArrayList<Audio>) : RecyclerView.Adapter<AudioViewHolder>() {

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): AudioViewHolder {
        val itemView = LayoutInflater.from(viewGroup.context).inflate(R.layout.item_audio, viewGroup, false)
        return AudioViewHolder(itemView)
    }

    override fun onBindViewHolder(audioViewHolder: AudioViewHolder, i: Int) {

        val audio = mAudioList[i]

        audioViewHolder.setTitle(audio.title!!)
        audioViewHolder.setArtist(audio.artist!!)

        var size = audio.size!!.toLongOrNull()
        size = size!! /(1024*1024)
        audioViewHolder.setSize(size.toString() + "MB")

        val duration = audio.duration!!.toLongOrNull()!! /1000
        val min = duration/60
        val sec = duration%60

        audioViewHolder.setDuration("$min:$sec")
    }

    override fun getItemCount(): Int {
        return mAudioList.size
    }
}
