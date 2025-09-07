
import java.util.ArrayList;
import java.util.List;
import org.bytedeco.ffmpeg.avcodec.*;
import org.bytedeco.ffmpeg.avformat.*;
import org.bytedeco.ffmpeg.avutil.*;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swresample.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import org.bytedeco.javacpp.FloatPointer;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.DftNormalization;
import org.apache.commons.math3.transform.FastFourierTransformer;
import org.apache.commons.math3.transform.TransformType;

public class AviAudioProfiler {

    static final FastFourierTransformer FFT = new FastFourierTransformer(DftNormalization.UNITARY);

    public static void main(String[] args) {
        String inputFile = "D:\\mame\\snap\\bzone\\0000.avi";

        avformat_network_init();

        AVFormatContext fmtCtx = avformat_alloc_context();

        double durSec = (fmtCtx.duration() > 0) ? (fmtCtx.duration() / 1_000_000.0) : Double.POSITIVE_INFINITY;
        int secondsToAnalyze = (int) Math.ceil(Double.isFinite(durSec) ? durSec : 3600); // up to 1 hour if unknown

        if (avformat_open_input(fmtCtx, inputFile, null, null) < 0) {
            System.err.println("Cannot open input: " + inputFile);
            return;
        }
        if (avformat_find_stream_info(fmtCtx, (AVDictionary) null) < 0) {
            System.err.println("Cannot find stream info");
            avformat_close_input(fmtCtx);
            return;
        }

        // Find best audio stream
        int aIdx = av_find_best_stream(fmtCtx, AVMEDIA_TYPE_AUDIO, -1, -1, (AVCodec) null, 0);
        if (aIdx < 0) {
            System.err.println("No audio stream found.");
            avformat_close_input(fmtCtx);
            return;
        }

        AVStream aStream = fmtCtx.streams(aIdx);
        AVCodecParameters par = aStream.codecpar();

        // --- Decoder setup ---
        AVCodec dec = avcodec_find_decoder(par.codec_id());
        if (dec == null) {
            System.err.println("Decoder not found");
            avformat_close_input(fmtCtx);
            return;
        }

        AVCodecContext decCtx = avcodec_alloc_context3(dec);
        if (avcodec_parameters_to_context(decCtx, par) < 0) {
            System.err.println("params->ctx failed");
            avformat_close_input(fmtCtx);
            return;
        }
        if (avcodec_open2(decCtx, dec, (AVDictionary) null) < 0) {
            System.err.println("Cannot open decoder");
            avcodec_free_context(decCtx);
            avformat_close_input(fmtCtx);
            return;
        }

        // --- Print basic audio info ---
        String codecName = avcodec_get_name(par.codec_id()).getString();
        int inRate = decCtx.sample_rate();
        AVChannelLayout inChLayout = new AVChannelLayout();
        if (decCtx.ch_layout().nb_channels() > 0) {
            av_channel_layout_copy(inChLayout, decCtx.ch_layout());
        } else {
            av_channel_layout_default(inChLayout, 1);
        }
        int inCh = inChLayout.nb_channels();

        System.out.println("Audio codec: " + codecName + ", " + inCh + " ch, " + inRate + " Hz");

        // --- Resampler target: mono, float, keep original sample rate ---
        int outRate = inRate;
        int outFmt = AV_SAMPLE_FMT_FLT; // packed 32-bit float
        AVChannelLayout outChLayout = new AVChannelLayout();
        av_channel_layout_default(outChLayout, 1); // mono

        // --- Create & init resampler (new API) ---
        SwrContext swr = new SwrContext(null);
        if (swr_alloc_set_opts2(
                swr,
                outChLayout, outFmt, outRate,
                inChLayout, decCtx.sample_fmt(), decCtx.sample_rate(),
                0, null
        ) < 0 || swr_init(swr) < 0) {
            System.err.println("swr_init failed");
            swr_free(swr);
            avcodec_free_context(decCtx);
            avformat_close_input(fmtCtx);
            return;
        }

        // --- Packet/frames ---
        AVPacket pkt = av_packet_alloc();
        AVFrame frame = av_frame_alloc();
        AVFrame outFrame = av_frame_alloc();

        // Pre-set static params on outFrame
        av_channel_layout_copy(outFrame.ch_layout(), outChLayout);
        outFrame.sample_rate(outRate);
        outFrame.format(outFmt);

        // Collect up to N seconds of mono float samples
        int maxSamples = Math.max(outRate * secondsToAnalyze, 1);
        float[] pcm = new float[maxSamples];
        int filled = 0;

// --- Read/decode loop ---
        while (av_read_frame(fmtCtx, pkt) >= 0 && filled < maxSamples) {
            if (pkt.stream_index() != aIdx) {
                av_packet_unref(pkt);
                continue;
            }

            if (avcodec_send_packet(decCtx, pkt) < 0) {
                av_packet_unref(pkt);
                continue;
            }
            av_packet_unref(pkt);

            while (avcodec_receive_frame(decCtx, frame) == 0 && filled < maxSamples) {
                // compute dst sample capacity for this call (must be INT)
                long want = av_rescale_rnd(
                        swr_get_delay(swr, decCtx.sample_rate()) + frame.nb_samples(),
                        outRate, decCtx.sample_rate(), AV_ROUND_UP);
                int outNbSamples = toIntExactClamp(want);

                // reset + set audio params on outFrame every time
                av_frame_unref(outFrame);
                av_channel_layout_copy(outFrame.ch_layout(), outChLayout);
                outFrame.sample_rate(outRate);
                outFrame.format(outFmt);
                outFrame.nb_samples(outNbSamples);

                if (av_frame_get_buffer(outFrame, 0) < 0) {
                    System.err.println("alloc out buffer failed");
                    // ...cleanup & return (unchanged)...
                    return;
                }

                // NOTE: both counts are int
                int converted = swr_convert(
                        swr,
                        outFrame.data(), outNbSamples, // dst (PointerPointer, int)
                        frame.data(), frame.nb_samples() // src (PointerPointer, int)
                );
                if (converted < 0) {
                    System.err.println("swr_convert failed");
                    // ...cleanup & return...
                    return;
                }

                // copy packed mono floats from outFrame to pcm[]
                int outCh = outFrame.ch_layout().nb_channels(); // 1 for mono
                int n = Math.min(converted * outCh, maxSamples - filled);
                FloatPointer fp = new FloatPointer(outFrame.data(0));
                float[] tmp = new float[n];
                fp.get(tmp, 0, n);
                System.arraycopy(tmp, 0, pcm, filled, n);
                filled += n;

                av_frame_unref(frame);
                av_frame_unref(outFrame);
            }
        }

// Drain decoder (optional, same pattern)
        avcodec_send_packet(decCtx, null);
        while (avcodec_receive_frame(decCtx, frame) == 0 && filled < maxSamples) {
            long want = av_rescale_rnd(
                    swr_get_delay(swr, decCtx.sample_rate()) + frame.nb_samples(),
                    outRate, decCtx.sample_rate(), AV_ROUND_UP);
            int outNbSamples = toIntExactClamp(want);

            av_frame_unref(outFrame);
            av_channel_layout_copy(outFrame.ch_layout(), outChLayout);
            outFrame.sample_rate(outRate);
            outFrame.format(outFmt);
            outFrame.nb_samples(outNbSamples);

            if (av_frame_get_buffer(outFrame, 0) < 0) {
                break;
            }

            int converted = swr_convert(swr, outFrame.data(), outNbSamples, frame.data(), frame.nb_samples());
            if (converted <= 0) {
                break;
            }

            int outCh = outFrame.ch_layout().nb_channels();
            int n = Math.min(converted * outCh, maxSamples - filled);
            FloatPointer fp = new FloatPointer(outFrame.data(0));
            float[] tmp = new float[n];
            fp.get(tmp, 0, n);
            System.arraycopy(tmp, 0, pcm, filled, n);
            filled += n;

            av_frame_unref(frame);
            av_frame_unref(outFrame);
        }

        System.out.println("Collected samples: " + filled + " @ " + outRate + " Hz (mono float)");

        printSoundProfileASCII(pcm, filled, outRate);

        analyzeDetailedEngineProfile(pcm, filled, outRate);
        
        analyzeHarmonicsPerSecond(pcm, filled, outRate);

        // --- Cleanup ---
        swr_free(swr);
        av_frame_free(outFrame);
        av_frame_free(frame);
        av_packet_free(pkt);
        avcodec_free_context(decCtx);
        avformat_close_input(fmtCtx);
    }

    private static int toIntExactClamp(long v) {
        return (v > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (v < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) v);
    }

    static void printSoundProfileASCII(float[] pcm, int validSamples, int sampleRate) {
        int seconds = validSamples / sampleRate;
        if (seconds == 0) {
            System.out.println("Not enough audio to profile.");
            return;
        }
        System.out.println();
        System.out.println("=== Per-second sound profile ===");
        System.out.println("sec | level (dBFS)  | peak Hz | centroid Hz | graph");
        System.out.println("----+---------------+---------+-------------+-----------------------------------------------");

        for (int s = 0; s < seconds; s++) {
            int start = s * sampleRate;
            int len = Math.min(sampleRate, validSamples - start);
            if (len <= 0) {
                break;
            }

            // 1) RMS level over this second
            double sumSq = 0.0;
            for (int i = 0; i < len; i++) {
                double v = pcm[start + i];
                sumSq += v * v;
            }
            double rms = Math.sqrt(sumSq / len);
            double dBFS = 20.0 * Math.log10(rms + 1e-12); // full-scale float = 1.0

            // 2) Frequency features via FFT on a Hann-windowed slice
            // Use largest power-of-two <= len (cap for speed if you like)
            int Nfft = nextPow2Below(len);
            if (Nfft > 8192) {
                Nfft = 8192;        // cap to keep it fast
            }
            if (Nfft < 2048) {
                Nfft = Math.min(len, 2048); // try to keep some resolution
            }
            if (Nfft < 256) {
                Nfft = len;         // very short segment fallback
            }
            double[] win = hann(Nfft);
            double[] x = new double[Nfft];
            for (int i = 0; i < Nfft; i++) {
                x[i] = pcm[start + i] * win[i];
            }

            FastFourierTransformer fft = new FastFourierTransformer(DftNormalization.UNITARY);
            Complex[] X = fft.transform(x, TransformType.FORWARD);

            // One-sided magnitude spectrum
            int nBins = Nfft / 2 + 1;
            double[] mag = new double[nBins];
            for (int k = 0; k < nBins; k++) {
                mag[k] = hypot(X[k].getReal(), X[k].getImaginary());
            }

            // Dominant frequency (ignore sub-20 Hz)
            double binHz = sampleRate / (double) Nfft;
            int startBin = (int) Math.ceil(20.0 / binHz);
            if (startBin < 1) {
                startBin = 1;
            }
            int peakBin = startBin;
            double peakVal = -1;
            for (int k = startBin; k < nBins; k++) {
                if (mag[k] > peakVal) {
                    peakVal = mag[k];
                    peakBin = k;
                }
            }
            double peakHz = peakBin * binHz;

            // Spectral centroid
            double sumMag = 0.0, sumFreqMag = 0.0;
            for (int k = 0; k < nBins; k++) {
                double f = k * binHz, m = mag[k];
                sumMag += m;
                sumFreqMag += f * m;
            }
            double centroid = (sumMag > 0) ? (sumFreqMag / sumMag) : 0.0;

            // ASCII bar: map dBFS from [-60, 0] -> [0, 47] characters
            int barLen = mapDbToBar(dBFS, -60.0, 0.0, 47);
            String bar = "#".repeat(Math.max(0, barLen));

            System.out.printf("%3d | %8.2f dBFS | %7.1f | %11.1f | %-47s%n",
                    s, dBFS, peakHz, centroid, bar);
        }
        System.out.println();
    }

    static int nextPow2Below(int n) {
        int p = 1;
        while ((p << 1) <= n) {
            p <<= 1;
        }
        return p;
    }

    static double[] hann(int N) {
        double[] w = new double[N];
        if (N == 1) {
            w[0] = 1.0;
            return w;
        }
        for (int n = 0; n < N; n++) {
            w[n] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * n / (N - 1)));
        }
        return w;
    }

    static double hypot(double a, double b) {
        return Math.hypot(a, b);
    }

    /**
     * Map dBFS in [minDb, maxDb] to [0, maxChars]
     */
    static int mapDbToBar(double dB, double minDb, double maxDb, int maxChars) {
        if (dB <= minDb) {
            return 0;
        }
        if (dB >= maxDb) {
            return maxChars;
        }
        double t = (dB - minDb) / (maxDb - minDb);
        return (int) Math.round(t * maxChars);
    }

    /**
     * It prints a per-second table with:
     *
     * RMS level (dBFS)
     *
     * Peak frequency (from FFT)
     *
     * F0 via autocorrelation (more robust “engine order” estimate)
     *
     * RPM estimate from low-freq peak (≈ peakHz×60)
     *
     * Spectral centroid, rolloff 85%/95%, flatness, zero-crossing rate
     *
     * Band energies (Low <200 Hz, Mid 200–1000 Hz, High >1 kHz)
     *
     * AM rate (dominant amplitude modulation under 20 Hz)
     *
     * @param pcm
     * @param validSamples
     * @param sr
     */
    static void analyzeDetailedEngineProfile(float[] pcm, int validSamples, int sr) {
        int seconds = validSamples / sr;
        if (seconds == 0) {
            System.out.println("Not enough audio to profile.");
            return;
        }
        System.out.println();
        System.out.println("=== Engine Sound Profile (per second) ===");
        System.out.println("sec | dBFS  | peakHz |  F0(ACF) |  RPM  | centroid | roll85 | roll95 | flatn |  ZCR  |  dB<200 | dB200-1k | dB>1k | AMrate");
        System.out.println("----+-------+--------+----------+-------+----------+--------+--------+-------+-------+--------+----------+-------+-------");

        double[] amWork = new double[1024]; // reused small buffers
        for (int s = 0; s < seconds; s++) {
            int off = s * sr, len = Math.min(sr, validSamples - off);
            if (len <= 0) {
                break;
            }

            // Slice
            float[] x = new float[len];
            System.arraycopy(pcm, off, x, 0, len);

            // RMS / dBFS
            double sumSq = 0.0;
            for (float v : x) {
                sumSq += v * v;
            }
            double rms = Math.sqrt(sumSq / Math.max(1, len));
            double dBFS = 20.0 * Math.log10(rms + 1e-12);

            // FFT block (Hann)
            int N = nextPow2Below(len);
            N = Math.max(2048, Math.min(N, 8192)); // clamp for stability
            double[] w = hann(N), xd = new double[N];
            for (int i = 0; i < N; i++) {
                xd[i] = (i < len ? x[i] : 0.0) * w[i];
            }
            Complex[] X = FFT.transform(xd, TransformType.FORWARD);

            // Magnitude spectrum (one-sided)
            int nBins = N / 2 + 1;
            double[] mag = new double[nBins];
            for (int k = 0; k < nBins; k++) {
                mag[k] = Math.hypot(X[k].getReal(), X[k].getImaginary());
            }
            double binHz = sr / (double) N;

            // Peak frequency (ignore <20 Hz)
            int startBin = Math.max(1, (int) Math.ceil(20.0 / binHz));
            int peakBin = startBin;
            double peakVal = -1;
            for (int k = startBin; k < nBins; k++) {
                if (mag[k] > peakVal) {
                    peakVal = mag[k];
                    peakBin = k;
                }
            }
            double peakHz = peakBin * binHz;

            // Spectral centroid
            double sumMag = 0, sumFreqMag = 0;
            for (int k = 0; k < nBins; k++) {
                double m = mag[k];
                sumMag += m;
                sumFreqMag += m * (k * binHz);
            }
            double centroid = sumMag > 0 ? sumFreqMag / sumMag : 0.0;

            // Rolloff 85% & 95%
            double roll85 = spectralRolloff(mag, 0.85, binHz);
            double roll95 = spectralRolloff(mag, 0.95, binHz);

            // Spectral flatness (geometric / arithmetic mean)
            double flat = spectralFlatness(mag);

            // Zero-crossing rate
            double zcr = zeroCrossingRate(x) * sr / (double) len; // crossings per second

            // Band energies
            double dBLow = bandDb(mag, binHz, 0, 200);
            double dBMid = bandDb(mag, binHz, 200, 1000);
            double dBHigh = bandDb(mag, binHz, 1000, sr / 2.0);

            // F0 via autocorrelation (robust fundamental)
            double f0 = estimateF0Autocorr(x, sr, 20, 400); // search 20–400 Hz for engine base order

            // RPM estimate from low peak (use lower of peakHz & f0 when both valid)
            double baseHz = (f0 > 0 ? f0 : peakHz);
            double rpm = baseHz > 0 ? baseHz * 60.0 : 0.0;

            // AM (amplitude modulation) rate under 20 Hz
            double amRate = estimateAMRate(x, sr);

            System.out.printf("%3d | %5.1f | %6.1f | %8.1f | %5.0f | %8.1f | %6.0f | %6.0f | %5.2f | %5.1f | %7.1f | %8.1f | %5.1f | %5.1f%n",
                    s, dBFS, peakHz, f0, rpm, centroid, roll85, roll95, flat, zcr, dBLow, dBMid, dBHigh, amRate);
        }
        System.out.println();
    }

    static double spectralRolloff(double[] mag, double frac, double binHz) {
        double total = 0;
        for (double m : mag) {
            total += m;
        }
        double target = total * frac, cum = 0;
        for (int k = 0; k < mag.length; k++) {
            cum += mag[k];
            if (cum >= target) {
                return k * binHz;
            }
        }
        return (mag.length - 1) * binHz;
    }

    static double spectralFlatness(double[] mag) {
        double geo = 0, arith = 0;
        int n = 0;
        for (double m : mag) {
            if (m > 0) {
                geo += Math.log(m);
                arith += m;
                n++;
            }
        }
        if (n == 0) {
            return 0;
        }
        geo = Math.exp(geo / n);
        arith /= n;
        return geo / (arith + 1e-12);
    }

    static double zeroCrossingRate(float[] x) {
        int z = 0;
        for (int i = 1; i < x.length; i++) {
            if ((x[i - 1] >= 0) != (x[i] >= 0)) {
                z++;
            }
        }
        return z / (double) x.length;
    }

    static double bandDb(double[] mag, double binHz, double fLo, double fHi) {
        int kLo = (int) Math.floor(fLo / binHz);
        int kHi = Math.min(mag.length - 1, (int) Math.ceil(fHi / binHz));
        double e = 0;
        for (int k = Math.max(0, kLo); k <= kHi; k++) {
            e += mag[k] * mag[k];
        }
        double rms = Math.sqrt(e / Math.max(1, (kHi - kLo + 1)));
        return 20 * Math.log10(rms + 1e-12);
    }

    static double estimateF0Autocorr(float[] x, int sr, double fMin, double fMax) {
        int minLag = (int) Math.floor(sr / fMax);
        int maxLag = (int) Math.ceil(sr / fMin);
        minLag = Math.max(1, minLag);
        maxLag = Math.min(x.length - 1, Math.max(minLag + 1, maxLag));

        // center & window a chunk (avoid DC bias)
        int N = Math.min(x.length, 4096);
        double mean = 0;
        for (int i = 0; i < N; i++) {
            mean += x[i];
        }
        mean /= N;
        double[] y = new double[N];
        for (int i = 0; i < N; i++) {
            double w = 0.5 * (1.0 - Math.cos(2 * Math.PI * i / (N - 1)));
            y[i] = (x[i] - mean) * w;
        }

        // normalized autocorr
        double denom = 0;
        for (int i = 0; i < N; i++) {
            denom += y[i] * y[i];
        }
        if (denom < 1e-12) {
            return 0;
        }

        double best = 0;
        int bestLag = minLag;
        for (int L = minLag; L <= maxLag; L++) {
            double r = 0;
            for (int i = 0; i < N - L; i++) {
                r += y[i] * y[i + L];
            }
            r /= denom;
            if (r > best) {
                best = r;
                bestLag = L;
            }
        }
        if (best < 0.1) {
            return 0; // weak periodicity
        }
        return sr / (double) bestLag;
    }

    static double estimateAMRate(float[] x, int sr) {
        // crude envelope: rectified + lowpass via downsample FFT
        int N = Math.min(x.length, 4096);
        if (N < 512) {
            return 0;
        }
        double[] env = new double[N];
        double alpha = Math.exp(-1.0 / (sr * 0.01)); // ~10ms smoothing
        double e = 0;
        for (int i = 0; i < N; i++) {
            e = alpha * e + (1 - alpha) * Math.abs(x[i]);
            env[i] = e;
        }

        // FFT envelope, find peak < 20 Hz
        int M = nextPow2Below(N);
        double[] w = hann(M), d = new double[M];
        for (int i = 0; i < M; i++) {
            d[i] = (i < N ? env[i] : 0.0) * w[i];
        }
        Complex[] E = FFT.transform(d, TransformType.FORWARD);
        int nBins = M / 2 + 1;
        double binHz = sr / (double) M;
        int hi = Math.min(nBins - 1, (int) Math.floor(20.0 / binHz));
        int pk = 1;
        double pv = -1;
        for (int k = 1; k <= hi; k++) {
            double m = Math.hypot(E[k].getReal(), E[k].getImaginary());
            if (m > pv) {
                pv = m;
                pk = k;
            }
        }
        return pk * binHz;
    }

    static void analyzeHarmonicsPerSecond(float[] pcm, int valid, int sr) {
        int seconds = valid / sr;
        if (seconds == 0) {
            return;
        }

        System.out.println("=== Harmonic peaks (per second) ===");
        System.out.println("sec |  F0  | peaks(Hz@dB) up to 1 kHz | HNR(dB)");
        System.out.println("----+------+---------------------------+--------");

        for (int s = 0; s < seconds; s++) {
            int off = s * sr, len = Math.min(sr, valid - off);
            if (len <= 256) {
                break;
            }

            // window + FFT
            int N = nextPow2Below(len);
            N = Math.max(2048, Math.min(N, 8192));
            double[] w = hann(N), x = new double[N];
            for (int i = 0; i < N; i++) {
                x[i] = (i < len ? pcm[off + i] : 0) * w[i];
            }
            Complex[] X = FFT.transform(x, TransformType.FORWARD);
            int nBins = N / 2 + 1;
            double binHz = sr / (double) N;

            // mag spectrum
            double[] mag = new double[nBins];
            for (int k = 0; k < nBins; k++) {
                mag[k] = Math.hypot(X[k].getReal(), X[k].getImaginary());
            }

            // F0 (tighten to realistic engine range 20..120 Hz)
            double f0 = estimateF0AutocorrTight(pcm, off, len, sr, 20, 120);

            // peak picking under 1 kHz
            List<double[]> peaks = topPeaks(mag, binHz, 20, 1000, 8); // up to 8 peaks
            double hnr = estimateHNR(mag, binHz, f0);

            System.out.printf("%3d | %5.1f | ", s, f0);
            for (int i = 0; i < peaks.size(); i++) {
                double[] p = peaks.get(i); // [freq, dB]
                if (i > 0) {
                    System.out.print(", ");
                }
                System.out.printf("%.0f@%.1f", p[0], p[1]);
            }
            if (peaks.isEmpty()) {
                System.out.print("-");
            }
            System.out.printf(" | %6.1f%n", hnr);
        }
        System.out.println();
    }

    static double estimateF0AutocorrTight(float[] pcm, int off, int len, int sr, double fMin, double fMax) {
        int minLag = (int) Math.floor(sr / fMax), maxLag = (int) Math.ceil(sr / fMin);
        minLag = Math.max(1, minLag);
        maxLag = Math.min(len - 1, Math.max(minLag + 1, maxLag));
        int N = Math.min(len, 4096);
        double mean = 0;
        for (int i = 0; i < N; i++) {
            mean += pcm[off + i];
        }
        mean /= N;
        double[] y = new double[N];
        for (int i = 0; i < N; i++) {
            double w = 0.5 * (1.0 - Math.cos(2 * Math.PI * i / (N - 1)));
            y[i] = (pcm[off + i] - mean) * w;
        }
        double denom = 0;
        for (double v : y) {
            denom += v * v;
        }
        if (denom < 1e-9) {
            return 0;
        }
        double best = 0;
        int bestLag = minLag;
        for (int L = minLag; L <= maxLag; L++) {
            double r = 0;
            for (int i = 0; i + L < N; i++) {
                r += y[i] * y[i + L];
            }
            r /= denom;
            if (r > best) {
                best = r;
                bestLag = L;
            }
        }
        if (best < 0.15) {
            return 0; // require stronger periodicity
        }
        return sr / (double) bestLag;
    }

    static List<double[]> topPeaks(double[] mag, double binHz, double fLo, double fHi, int k) {
        int lo = Math.max(1, (int) Math.floor(fLo / binHz));
        int hi = Math.min(mag.length - 2, (int) Math.ceil(fHi / binHz));
        List<double[]> list = new ArrayList<>();
        // local maxima
        for (int i = lo + 1; i <= hi - 1; i++) {
            if (mag[i] > mag[i - 1] && mag[i] > mag[i + 1]) {
                list.add(new double[]{i * binHz, mag[i]});
            }
        }
        // sort by magnitude desc
        list.sort((a, b) -> Double.compare(b[1], a[1]));
        // convert mag→dB rel. to RMS of band to stabilize values
        double rms = 0;
        int n = 0;
        for (int i = lo; i <= hi; i++) {
            rms += mag[i] * mag[i];
            n++;
        }
        rms = Math.sqrt(rms / Math.max(1, n));
        int keep = Math.min(k, list.size());
        List<double[]> out = new ArrayList<>(keep);
        for (int i = 0; i < keep; i++) {
            double f = list.get(i)[0], m = list.get(i)[1];
            double db = 20 * Math.log10((m / (rms + 1e-12)) + 1e-12);
            out.add(new double[]{f, db});
        }
        return out;
    }

    static double estimateHNR(double[] mag, double binHz, double f0) {
        if (f0 <= 0) {
            return 0;
        }
        // sum narrow bands around k*f0 as "harmonic", rest as "noise"
        double harm = 0, noise = 0;
        double bw = 0.05 * f0; // ±5% of f0 per harmonic
        int kmax = (int) Math.floor(1000.0 / f0); // up to 1 kHz
        for (int k = 1; k <= kmax; k++) {
            double fk = k * f0;
            int lo = (int) Math.floor(Math.max(1, (fk - bw) / binHz));
            int hi = (int) Math.ceil((fk + bw) / binHz);
            for (int i = lo; i <= hi && i < mag.length; i++) {
                harm += mag[i] * mag[i];
            }
        }
        // total up to 1 kHz
        int allHi = Math.min(mag.length - 1, (int) Math.floor(1000.0 / binHz));
        double tot = 0;
        for (int i = 1; i <= allHi; i++) {
            tot += mag[i] * mag[i];
        }
        noise = Math.max(1e-12, tot - harm);
        return 10 * Math.log10(harm / noise);
    }

}
