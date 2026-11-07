import AVFoundation
import SwiftUI
import YaadeinData
import YaadeinDomain

/// Records AAC audio to a temporary file (deleted after it is encrypted into the vault).
@MainActor
@Observable
final class VoiceRecorder: NSObject, AVAudioRecorderDelegate {
    var recording = false
    var elapsed: TimeInterval = 0
    var file: URL?
    var failed = false
    private var recorder: AVAudioRecorder?
    private var timer: Timer?

    func start() async {
        failed = false
        guard await AVAudioApplication.requestRecordPermission() else {
            failed = true
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .spokenAudio, options: [.defaultToSpeaker])
            try session.setActive(true)
            discard()
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("voice-\(UUID().uuidString).m4a")
            let r = try AVAudioRecorder(url: url, settings: [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 44_100,
                                                             AVNumberOfChannelsKey: 1, AVEncoderBitRateKey: 96_000])
            r.delegate = self
            guard r.record() else { throw CocoaError(.fileWriteUnknown) }
            recorder = r
            file = url
            recording = true
            elapsed = 0
            timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
                Task { @MainActor in self?.elapsed = self?.recorder?.currentTime ?? 0 }
            }
        } catch {
            failed = true
        }
    }

    func stop() {
        recorder?.stop()
        recorder = nil
        timer?.invalidate()
        recording = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func discard() {
        stop()
        if let file { try? FileManager.default.removeItem(at: file) }
        file = nil
        elapsed = 0
    }

    var elapsedText: String { String(format: "%d:%02d", Int(elapsed) / 60, Int(elapsed) % 60) }
}

/// Voice memory (VoiceCaptureScreen.kt): record, title, whose voice, save as a memory; or add a voice note to an
/// existing memory when `memoryId` is set.
struct VoiceCaptureView: View {
    var memoryId: Int64? = nil
    var prompt: String? = nil
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @Environment(\.dismiss) private var dismiss
    @State private var recorder = VoiceRecorder()
    @State private var title = ""
    @State private var people: [Int64] = []
    @State private var saving = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.lg) {
                if let prompt { Text(prompt).font(look.type.titleLarge).foregroundStyle(look.colors.primary) }
                Text(L("voice_help")).font(look.type.bodyLarge).foregroundStyle(look.colors.onSurfaceVariant)
                HStack {
                    Spacer()
                    Button {
                        if recorder.recording { recorder.stop() } else { Task { await recorder.start() } }
                    } label: {
                        Image(systemName: recorder.recording ? "stop.fill" : "mic.fill")
                            .font(.system(size: 44)).foregroundStyle(look.colors.onPrimary)
                            .frame(width: 120, height: 120)
                            .background(Circle().fill(recorder.recording ? look.colors.error : look.colors.primary))
                    }
                    .accessibilityLabel(L(recorder.recording ? "voice_stop" : "voice_start"))
                    Spacer()
                }
                Text(recorder.recording ? recorder.elapsedText : (recorder.file != nil ? L("voice_recorded", recorder.elapsedText) : L("voice_tap_to_record")))
                    .font(look.type.titleMedium).frame(maxWidth: .infinity)
                if recorder.failed { Text(L("voice_mic_failed")).font(look.type.bodyMedium).foregroundStyle(look.colors.error) }
                if recorder.file != nil && !recorder.recording {
                    Button(L("voice_record_again")) { recorder.discard() }.buttonStyle(TextButtonStyle()).frame(maxWidth: .infinity)
                }
                if memoryId == nil {
                    OutlinedField(label: L("voice_title_field"), text: $title)
                    PeopleChips(selected: $people, title: L("voice_who"))
                }
                Button(L("voice_save")) { Task { await save() } }
                    .buttonStyle(FilledButtonStyle(fullWidth: true))
                    .disabled(recorder.file == nil || recorder.recording || saving)
                Text(L("voice_privacy")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant)
            }
            .padding(Spacing.gutter)
        }
        .screenTitle(L("voice_title"))
        .onDisappear { if !saving { recorder.discard() } }
    }

    private func save() async {
        guard let file = recorder.file, let store = model.store, let vault = model.vault else { return }
        saving = true
        defer { recorder.discard(); saving = false }
        guard let stored = try? vault.store(file: file, folder: .voice, ext: "m4a") else { return }
        if let memoryId {
            if let old = store.memory(id: memoryId)?.voiceNotePath { vault.delete(old) }
            try? store.setVoiceNote(memoryId: memoryId, path: stored.path)
        } else {
            let who = people.first.flatMap { store.person(id: $0)?.displayName }
            let t = title.trimmingCharacters(in: .whitespaces)
            let memory = Memory(type: .QUICK, title: t.isEmpty ? (who.map { L("voice_title_person", $0) } ?? L("voice_title_default")) : t,
                                description: prompt ?? "", date: Dates.exact(.today()), voiceNotePath: stored.path)
            _ = try? store.createMemory(memory, personIds: people)
        }
        dismiss()
    }
}

/// Voice note player card on the memory (VoiceNoteCard.kt), streamed from the encrypted vault file.
struct VoiceNoteCard: View {
    let memoryId: Int64
    let path: String
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var player: AVPlayer?
    @State private var loader: EncryptedVideoLoader?
    @State private var playing = false

    var body: some View {
        HStack(spacing: Spacing.md) {
            Button {
                if player == nil, let vault = model.vault, let keys = try? model.session.currentKeys(),
                   let made = EncryptedVideoLoader.player(path: path, vault: vault, aead: model.aead, key: keys.media) {
                    player = made.0
                    loader = made.1
                }
                if playing { player?.pause() } else { player?.play() }
                playing.toggle()
            } label: {
                Image(systemName: playing ? "pause.circle.fill" : "play.circle.fill").font(.system(size: 40)).foregroundStyle(look.colors.primary)
            }
            .accessibilityLabel(L(playing ? "memory_voice_pause" : "memory_voice_play"))
            Text(L("memory_voice_title")).font(look.type.titleMedium)
            Spacer()
            Button(L("memory_voice_remove")) {
                player?.pause()
                try? model.store?.setVoiceNote(memoryId: memoryId, path: nil)
                model.vault?.delete(path)
            }
            .buttonStyle(TextButtonStyle())
        }
        .padding(Spacing.md)
        .background(RoundedRectangle(cornerRadius: Radius.md).fill(look.colors.surfaceContainerLow))
        .onDisappear { player?.pause() }
    }
}
