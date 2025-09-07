
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import org.bytedeco.javacpp.BytePointer;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swresample.*;

public class AviToOggClipper {

    private static final AVRational TB_MS = new AVRational().num(1).den(1000);

    public static void main(String[] args) throws Exception {

        String out = clipAviAudioToOgg("D:\\mame\\snap\\bzone\\0000.avi", 8200, 9200);
        System.out.println("Wrote: " + out);
    }

    public static String clipAviAudioToOgg(String inputPath, long startMs, long endMs) throws Exception {
        // Local timebases (avoid AV_TIME_BASE_Q())
        final AVRational TB_MS = new AVRational().num(1).den(1000);
        final AVRational TB_US = new AVRational().num(1).den(AV_TIME_BASE);

        if (startMs < 0 || endMs <= startMs) {
            throw new IllegalArgumentException("0 <= startMs < endMs required");
        }
        File inFile = new File(inputPath);
        if (!inFile.exists()) {
            throw new IllegalArgumentException("Not found: " + inputPath);
        }

        Path in = Paths.get(inputPath);
        String stem = in.getFileName().toString();
        int dot = stem.lastIndexOf('.');
        if (dot >= 0) {
            stem = stem.substring(0, dot);
        }
        String outPath = (in.getParent() == null)
                ? stem + "_clip_" + startMs + "-" + endMs + ".ogg"
                : in.getParent().resolve(stem + "_clip_" + startMs + "-" + endMs + ".ogg").toString();

        av_log_set_level(AV_LOG_ERROR);

        AVFormatContext ifmt = new AVFormatContext(null);
        int ret;

        // --- open input ---
        ret = avformat_open_input(ifmt, inputPath, null, (AVDictionary) null);
        if (ret < 0) {
            throw new RuntimeException("avformat_open_input: " + err(ret));
        }
        ret = avformat_find_stream_info(ifmt, (AVDictionary) null);
        if (ret < 0) {
            avformat_close_input(ifmt);
            throw new RuntimeException("avformat_find_stream_info: " + err(ret));
        }

        // --- audio stream ---
        int aIdx = av_find_best_stream(ifmt, AVMEDIA_TYPE_AUDIO, -1, -1, (AVCodec) null, 0);
        if (aIdx < 0) {
            avformat_close_input(ifmt);
            throw new RuntimeException("No audio stream");
        }
        AVStream inStream = ifmt.streams(aIdx);
        AVRational inTB = inStream.time_base();

        // --- decoder ---
        AVCodec dec = avcodec_find_decoder(inStream.codecpar().codec_id());
        if (dec == null) {
            avformat_close_input(ifmt);
            throw new RuntimeException("No decoder for codec id: " + inStream.codecpar().codec_id());
        }
        AVCodecContext decCtx = avcodec_alloc_context3(dec);
        ret = avcodec_parameters_to_context(decCtx, inStream.codecpar());
        if (ret < 0) {
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("avcodec_parameters_to_context: " + err(ret));
        }
        ret = avcodec_open2(decCtx, dec, (AVDictionary) null);
        if (ret < 0) {
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("avcodec_open2(dec): " + err(ret));
        }

        // Ensure input channel layout defined and record channel count
        AVChannelLayout inCh = new AVChannelLayout();
        av_channel_layout_copy(inCh, decCtx.ch_layout());
        if (inCh.nb_channels() == 0) {
            av_channel_layout_default(inCh, 2); // safe default
        }
        final int inChannels = inCh.nb_channels();

        // --- seek window ---
        long startTS = av_rescale_q(startMs, TB_MS, inTB);
        long endTS = av_rescale_q(endMs, TB_MS, inTB);
        ret = av_seek_frame(ifmt, aIdx, startTS, AVSEEK_FLAG_BACKWARD);
        if (ret < 0) {
            av_seek_frame(ifmt, -1, av_rescale_q(startMs, TB_MS, TB_US), AVSEEK_FLAG_BACKWARD);
        }
        avcodec_flush_buffers(decCtx);

        // --- output context ---
        AVFormatContext ofmt = allocOutputContext("ogg", outPath);

        // Prefer libvorbis; otherwise native vorbis (experimental, needs 2ch)
        AVCodec enc = avcodec_find_encoder_by_name(new BytePointer("libvorbis"));
        boolean usingNativeVorbis = false;
        if (enc == null) {
            enc = avcodec_find_encoder(AV_CODEC_ID_VORBIS);
            usingNativeVorbis = true;
        }
        if (enc == null) {
            avformat_free_context(ofmt);
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("No Vorbis encoder available (libvorbis or native).");
        }

        AVStream outStream = avformat_new_stream(ofmt, (AVCodec) null);
        if (outStream == null) {
            avformat_free_context(ofmt);
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("avformat_new_stream failed");
        }

        // --- encoder ctx ---
        AVCodecContext encCtx = avcodec_alloc_context3(enc);
        int outRate = decCtx.sample_rate() > 0 ? decCtx.sample_rate() : 48000;
        encCtx.sample_rate(outRate);

        if (enc.sample_fmts() != null && enc.sample_fmts().limit() > 0) {
            encCtx.sample_fmt(enc.sample_fmts().get(0));
        } else {
            encCtx.sample_fmt(AV_SAMPLE_FMT_FLTP);
        }

        // Channel layout: if native vorbis and not 2ch, force stereo BEFORE open
        AVChannelLayout outCh = new AVChannelLayout();
        if (usingNativeVorbis && inChannels != 2) {
            av_channel_layout_default(outCh, 2);  // upmix mono or downmix >2ch
        } else {
            av_channel_layout_copy(outCh, inCh);
            if (outCh.nb_channels() == 0) {
                av_channel_layout_default(outCh, Math.max(1, inChannels));
            }
        }
        encCtx.ch_layout(outCh);

        AVRational encTB = new AVRational().num(1).den(outRate);
        encCtx.time_base(encTB);
        encCtx.bit_rate(192_000);
        if ((ofmt.oformat().flags() & AVFMT_GLOBALHEADER) != 0) {
            encCtx.flags(encCtx.flags() | AV_CODEC_FLAG_GLOBAL_HEADER);
        }

        // Allow experimental only if using native vorbis
        AVDictionary opts = null;
        if (usingNativeVorbis) {
            encCtx.strict_std_compliance(FF_COMPLIANCE_EXPERIMENTAL); // -2
            opts = new AVDictionary(null);
            av_dict_set(opts, "strict", "-2", 0);
        }

        // open encoder
        ret = avcodec_open2(encCtx, enc, opts);
        if (opts != null) {
            av_dict_free(opts);
        }
        if (ret < 0) {
            avcodec_free_context(encCtx);
            avformat_free_context(ofmt);
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("avcodec_open2(enc): " + err(ret));
        }

        ret = avcodec_parameters_from_context(outStream.codecpar(), encCtx);
        if (ret < 0) {
            avcodec_free_context(encCtx);
            avformat_free_context(ofmt);
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("avcodec_parameters_from_context: " + err(ret));
        }
        outStream.time_base(encTB);

        // open IO
        if ((ofmt.oformat().flags() & AVFMT_NOFILE) == 0) {
            AVIOContext pb = new AVIOContext(null);
            ret = avio_open(pb, new BytePointer(outPath), AVIO_FLAG_WRITE);
            if (ret < 0) {
                avcodec_free_context(encCtx);
                avformat_free_context(ofmt);
                avcodec_free_context(decCtx);
                avformat_close_input(ifmt);
                throw new RuntimeException("avio_open: " + err(ret));
            }
            ofmt.pb(pb);
        }

        ret = avformat_write_header(ofmt, (AVDictionary) null);
        if (ret < 0) {
            if ((ofmt.oformat().flags() & AVFMT_NOFILE) == 0 && ofmt.pb() != null) {
                avio_closep(ofmt.pb());
            }
            avcodec_free_context(encCtx);
            avformat_free_context(ofmt);
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("avformat_write_header: " + err(ret));
        }

        // --- resampler + chunker to honor encCtx.frame_size() ---
        SwrContext swr = new SwrContext(null);
        ret = swr_alloc_set_opts2(
                swr,
                encCtx.ch_layout(), encCtx.sample_fmt(), encCtx.sample_rate(),
                decCtx.ch_layout(), decCtx.sample_fmt(), decCtx.sample_rate(),
                0, null);
        if (ret < 0 || (ret = swr_init(swr)) < 0) {
            if ((ofmt.oformat().flags() & AVFMT_NOFILE) == 0 && ofmt.pb() != null) {
                avio_closep(ofmt.pb());
            }
            avcodec_free_context(encCtx);
            avformat_free_context(ofmt);
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("swr_init: " + err(ret));
        }
        int encFrameSize = encCtx.frame_size();
        if (encFrameSize <= 0) {
            encFrameSize = 1024; // fallback
        }
        // --- processing loop ---
        AVPacket ipkt = av_packet_alloc();
        AVFrame dfrm = av_frame_alloc();
        AVFrame efrm = av_frame_alloc();
        if (ipkt == null || dfrm == null || efrm == null) {
            if ((ofmt.oformat().flags() & AVFMT_NOFILE) == 0 && ofmt.pb() != null) {
                avio_closep(ofmt.pb());
            }
            if (swr != null) {
                swr_free(swr);
            }
            avcodec_free_context(encCtx);
            avformat_free_context(ofmt);
            avcodec_free_context(decCtx);
            avformat_close_input(ifmt);
            throw new RuntimeException("Allocation failed");
        }

        long nextPts = 0; // samples @ encTB (1/outRate)
        boolean started = false, done = false;

        while (!done && (ret = av_read_frame(ifmt, ipkt)) >= 0) {
            try {
                if (ipkt.stream_index() != aIdx) {
                    continue;
                }

                ret = avcodec_send_packet(decCtx, ipkt);
                if (ret < 0) {
                    throw new RuntimeException("avcodec_send_packet: " + err(ret));
                }

                while ((ret = avcodec_receive_frame(decCtx, dfrm)) >= 0) {
                    long pts = dfrm.best_effort_timestamp();
                    if (pts == AV_NOPTS_VALUE) {
                        pts = dfrm.pts();
                    }
                    long frameStartMs = av_rescale_q(pts, inTB, TB_MS);
                    long frameEndMs = frameStartMs + frameDurationMs(dfrm, inTB, decCtx.sample_rate());

                    if (!started) {
                        if (frameEndMs <= startMs) {
                            continue;
                        }
                        started = true;
                    }
                    if (frameStartMs >= endMs) {
                        done = true;
                        break;
                    }

                    // 1) Convert & emit first chunk (<= encFrameSize)
                    int outWanted = encFrameSize;
                    av_frame_unref(efrm);
                    efrm.format(encCtx.sample_fmt());
                    efrm.sample_rate(encCtx.sample_rate());
                    efrm.nb_samples(outWanted);
                    av_channel_layout_copy(efrm.ch_layout(), encCtx.ch_layout());
                    int bufOk = av_frame_get_buffer(efrm, 0);
                    if (bufOk < 0) {
                        throw new RuntimeException("av_frame_get_buffer: " + err(bufOk));
                    }

                    int outSamples = swr_convert(
                            swr,
                            efrm.extended_data(), outWanted,
                            dfrm.extended_data(), dfrm.nb_samples());
                    if (outSamples < 0) {
                        throw new RuntimeException("swr_convert: " + err(outSamples));
                    }

                    if (outSamples > 0) {
                        long encStartMs = av_rescale_q(nextPts, encTB, TB_MS);
                        long encEndMs = encStartMs + (outSamples * 1000L / encCtx.sample_rate());
                        if (encStartMs >= endMs) {
                            done = true;
                        }
                        if (!done) {
                            if (encEndMs > endMs) {
                                int allow = (int) Math.max(0, Math.min(outSamples,
                                        ((endMs - encStartMs) * encCtx.sample_rate() + 999) / 1000));
                                if (allow <= 0) {
                                    done = true;
                                } else {
                                    outSamples = allow;
                                }
                            }
                            if (!done) {
                                efrm.nb_samples(outSamples);
                                efrm.pts(nextPts);
                                nextPts += outSamples;

                                ret = avcodec_send_frame(encCtx, efrm);
                                if (ret < 0) {
                                    throw new RuntimeException("avcodec_send_frame: " + err(ret));
                                }

                                AVPacket opkt = av_packet_alloc();
                                try {
                                    while ((ret = avcodec_receive_packet(encCtx, opkt)) >= 0) {
                                        opkt.stream_index(outStream.index());
                                        av_packet_rescale_ts(opkt, encTB, outStream.time_base());
                                        ret = av_interleaved_write_frame(ofmt, opkt);
                                        if (ret < 0) {
                                            throw new RuntimeException("av_interleaved_write_frame: " + err(ret));
                                        }
                                        av_packet_unref(opkt);
                                    }
                                    if (ret != AVERROR_EAGAIN() && ret != AVERROR_EOF() && ret < 0) {
                                        throw new RuntimeException("avcodec_receive_packet: " + err(ret));
                                    }
                                } finally {
                                    av_packet_free(opkt);
                                }
                            }
                        }
                    }

                    // 2) Drain any additional full chunks now buffered in swr
                    while (!done && swr_get_out_samples(swr, 0) >= encFrameSize) {
                        av_frame_unref(efrm);
                        efrm.format(encCtx.sample_fmt());
                        efrm.sample_rate(encCtx.sample_rate());
                        efrm.nb_samples(encFrameSize);
                        av_channel_layout_copy(efrm.ch_layout(), encCtx.ch_layout());
                        int ok = av_frame_get_buffer(efrm, 0);
                        if (ok < 0) {
                            throw new RuntimeException("av_frame_get_buffer(drain): " + err(ok));
                        }

                        int got = swr_convert(swr, efrm.extended_data(), encFrameSize, null, 0);
                        if (got < 0) {
                            throw new RuntimeException("swr_convert(drain): " + err(got));
                        }
                        if (got == 0) {
                            break;
                        }

                        long encStartMs2 = av_rescale_q(nextPts, encTB, TB_MS);
                        long encEndMs2 = encStartMs2 + (got * 1000L / encCtx.sample_rate());
                        if (encStartMs2 >= endMs) {
                            done = true;
                            break;
                        }
                        if (encEndMs2 > endMs) {
                            int allow2 = (int) Math.max(0, Math.min(got,
                                    ((endMs - encStartMs2) * encCtx.sample_rate() + 999) / 1000));
                            if (allow2 <= 0) {
                                done = true;
                                break;
                            }
                            got = allow2;
                        }

                        efrm.nb_samples(got);
                        efrm.pts(nextPts);
                        nextPts += got;

                        ret = avcodec_send_frame(encCtx, efrm);
                        if (ret < 0) {
                            throw new RuntimeException("avcodec_send_frame: " + err(ret));
                        }

                        AVPacket opkt2 = av_packet_alloc();
                        try {
                            while ((ret = avcodec_receive_packet(encCtx, opkt2)) >= 0) {
                                opkt2.stream_index(outStream.index());
                                av_packet_rescale_ts(opkt2, encTB, outStream.time_base());
                                ret = av_interleaved_write_frame(ofmt, opkt2);
                                if (ret < 0) {
                                    throw new RuntimeException("av_interleaved_write_frame: " + err(ret));
                                }
                                av_packet_unref(opkt2);
                            }
                            if (ret != AVERROR_EAGAIN() && ret != AVERROR_EOF() && ret < 0) {
                                throw new RuntimeException("avcodec_receive_packet: " + err(ret));
                            }
                        } finally {
                            av_packet_free(opkt2);
                        }
                    }
                }

                if (ret != AVERROR_EAGAIN() && ret != AVERROR_EOF() && ret < 0) {
                    throw new RuntimeException("avcodec_receive_frame: " + err(ret));
                }
            } finally {
                av_packet_unref(ipkt);
            }
        }

        // Drain any leftover samples from swr after reading finishes
        while (!done && swr_get_out_samples(swr, 0) > 0) {
            int want = Math.min(swr_get_out_samples(swr, 0), encFrameSize);

            av_frame_unref(efrm);
            efrm.format(encCtx.sample_fmt());
            efrm.sample_rate(encCtx.sample_rate());
            efrm.nb_samples(want);
            av_channel_layout_copy(efrm.ch_layout(), encCtx.ch_layout());
            int ok2 = av_frame_get_buffer(efrm, 0);
            if (ok2 < 0) {
                throw new RuntimeException("av_frame_get_buffer(final drain): " + err(ok2));
            }

            int got2 = swr_convert(swr, efrm.extended_data(), want, null, 0);
            if (got2 < 0) {
                throw new RuntimeException("swr_convert(final drain): " + err(got2));
            }
            if (got2 == 0) {
                break;
            }

            long encStartMs3 = av_rescale_q(nextPts, encTB, TB_MS);
            long encEndMs3 = encStartMs3 + (got2 * 1000L / encCtx.sample_rate());
            if (encStartMs3 >= endMs) {
                break;
            }
            if (encEndMs3 > endMs) {
                int allow3 = (int) Math.max(0, Math.min(got2,
                        ((endMs - encStartMs3) * encCtx.sample_rate() + 999) / 1000));
                if (allow3 <= 0) {
                    break;
                }
                got2 = allow3;
            }

            efrm.nb_samples(got2);
            efrm.pts(nextPts);
            nextPts += got2;

            ret = avcodec_send_frame(encCtx, efrm);
            if (ret < 0) {
                throw new RuntimeException("avcodec_send_frame: " + err(ret));
            }

            AVPacket opkt3 = av_packet_alloc();
            try {
                while ((ret = avcodec_receive_packet(encCtx, opkt3)) >= 0) {
                    opkt3.stream_index(outStream.index());
                    av_packet_rescale_ts(opkt3, encTB, outStream.time_base());
                    ret = av_interleaved_write_frame(ofmt, opkt3);
                    if (ret < 0) {
                        throw new RuntimeException("av_interleaved_write_frame: " + err(ret));
                    }
                    av_packet_unref(opkt3);
                }
                if (ret != AVERROR_EAGAIN() && ret != AVERROR_EOF() && ret < 0) {
                    throw new RuntimeException("avcodec_receive_packet: " + err(ret));
                }
            } finally {
                av_packet_free(opkt3);
            }
        }

        // flush encoder
        if ((ret = avcodec_send_frame(encCtx, null)) >= 0) {
            AVPacket opkt = av_packet_alloc();
            try {
                while ((ret = avcodec_receive_packet(encCtx, opkt)) >= 0) {
                    opkt.stream_index(outStream.index());
                    av_packet_rescale_ts(opkt, encTB, outStream.time_base());
                    ret = av_interleaved_write_frame(ofmt, opkt);
                    if (ret < 0) {
                        throw new RuntimeException("flush write: " + err(ret));
                    }
                    av_packet_unref(opkt);
                }
            } finally {
                av_packet_free(opkt);
            }
        }

        // trailer and close
        ret = av_write_trailer(ofmt);
        if (ret < 0) {
            throw new RuntimeException("av_write_trailer: " + err(ret));
        }

        if (ipkt != null) {
            av_packet_free(ipkt);
        }
        if (efrm != null) {
            av_frame_free(efrm);
        }
        if (dfrm != null) {
            av_frame_free(dfrm);
        }
        if (swr != null) {
            swr_free(swr);
        }
        if ((ofmt.oformat().flags() & AVFMT_NOFILE) == 0 && ofmt.pb() != null) {
            avio_closep(ofmt.pb());
        }
        avcodec_free_context(encCtx);
        avformat_free_context(ofmt);
        avcodec_free_context(decCtx);
        avformat_close_input(ifmt);

        return outPath;
    }

    private static long frameDurationMs(AVFrame f, AVRational tb, int sampleRate) {
        long durTs = av_rescale_q(f.nb_samples(), new AVRational().num(1).den(sampleRate), tb);
        return av_rescale_q(durTs, tb, TB_MS);
    }

    private static String err(int ret) {
        byte[] buf = new byte[1024];
        av_strerror(ret, buf, buf.length);
        return new String(buf).trim();
    }

    private static AVFormatContext allocOutputContext(String muxer, String filename) {
        AVFormatContext ofmt = new AVFormatContext(null);
        int r = avformat_alloc_output_context2(
                ofmt,
                (AVOutputFormat) null,
                new BytePointer(muxer),
                new BytePointer(filename)
        );
        if (r < 0 || ofmt == null || ofmt.isNull() || ofmt.oformat() == null) {
            throw new RuntimeException("avformat_alloc_output_context2: " + err(r));
        }
        return ofmt;
    }

}
