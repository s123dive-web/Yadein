import Combine
import LocalAuthentication
import SwiftUI
import YaadeinData

let pinLength = 6

/// Six dots showing how many digits are entered (PinDots).
struct PinDots: View {
    let count: Int
    let error: Bool
    @Environment(\.look) private var look

    var body: some View {
        HStack(spacing: Spacing.md) {
            ForEach(0..<pinLength, id: \.self) { i in
                Circle()
                    .fill(error ? look.colors.error : (i < count ? look.colors.primary : look.colors.surfaceContainerHighest))
                    .frame(width: 16, height: 16)
            }
        }
        .accessibilityElement()
        .accessibilityLabel(L("lock_pin_entered", count, pinLength))
    }
}

/// The 3×4 PIN pad with Face ID / Touch ID and backspace keys (PinPad).
struct PinPad: View {
    let enabled: Bool
    let onDigit: (Character) -> Void
    let onBackspace: () -> Void
    var onBiometric: (() -> Void)? = nil
    @Environment(\.look) private var look

    var body: some View {
        let key = look.sizes.touchTarget + Spacing.xl
        VStack(spacing: Spacing.sm) {
            ForEach([["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"]], id: \.self) { row in
                HStack(spacing: Spacing.lg) {
                    ForEach(row, id: \.self) { d in digit(Character(d), key) }
                }
            }
            HStack(spacing: Spacing.lg) {
                ZStack {
                    if let onBiometric {
                        Button(action: onBiometric) {
                            Image(systemName: BiometricKeys.kind == .touchID ? "touchid" : "faceid")
                                .font(.system(size: look.sizes.iconLg))
                                .foregroundStyle(look.colors.primary)
                                .frame(width: key, height: key)
                        }
                        .accessibilityLabel(L("lock_use_biometric"))
                    }
                }
                .frame(width: key, height: key)
                digit("0", key)
                Button(action: onBackspace) {
                    Image(systemName: "delete.left").font(.system(size: look.sizes.icon)).frame(width: key, height: key)
                }
                .foregroundStyle(look.colors.onSurface)
                .accessibilityLabel(L("lock_backspace"))
            }
        }
        .disabled(!enabled)
        .frame(maxWidth: 360)
    }

    private func digit(_ d: Character, _ size: CGFloat) -> some View {
        Button { onDigit(d) } label: {
            Text(String(d))
                .font(look.type.headlineMedium)
                .foregroundStyle(look.colors.onSurface)
                .frame(width: size, height: size)
                .background(Circle().fill(look.colors.surfaceContainerLow))
        }
        .buttonStyle(.plain)
    }
}

/// Lock screen (LockScreen.kt): Face ID offered automatically, PIN pad fallback, escalating lockout, optional wipe.
struct LockView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var pin = ""
    @State private var checking = false
    @State private var status = ""
    @State private var wrong = false
    @State private var lockedUntil: Date?
    @State private var now = Date()
    private let timer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    var body: some View {
        if model.settings.lockMethod == .DEVICE {
            deviceLock
        } else {
            pinLock
        }
    }

    private var lockedForMs: Int64 {
        guard let lockedUntil else { return 0 }
        return max(0, Int64(lockedUntil.timeIntervalSince(now) * 1000))
    }

    private var pinLock: some View {
        VStack(spacing: 0) {
            Spacer()
            Illustration(kind: .lockHeart).frame(width: look.sizes.illustration * 0.7, height: look.sizes.illustration * 0.7)
            Spacer().frame(height: Spacing.lg)
            Text(L("lock_title")).font(look.type.headlineMedium).accessibilityAddTraits(.isHeader)
            Spacer().frame(height: Spacing.xs)
            Text(L("lock_subtitle")).font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.center)
            Spacer().frame(height: Spacing.xl)
            PinDots(count: pin.count, error: wrong)
            Spacer().frame(height: Spacing.md)
            Text(statusText).font(look.type.bodyMedium).foregroundStyle(look.colors.error).multilineTextAlignment(.center).frame(height: 40)
            Spacer().frame(height: Spacing.lg)
            PinPad(enabled: !checking && lockedForMs == 0, onDigit: onDigit, onBackspace: { if !pin.isEmpty { pin.removeLast() }; wrong = false },
                   onBiometric: model.settings.biometricEnabled ? { Task { await biometric() } } : nil)
            Spacer()
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(look.colors.surface.ignoresSafeArea())
        .onReceive(timer) { now = $0 }
        .task {
            let remaining = model.session.lockout.remainingMs
            if remaining > 0 { lockedUntil = Date().addingTimeInterval(Double(remaining) / 1000) }
            if model.settings.biometricEnabled && remaining == 0 { await biometric() }
        }
    }

    private var statusText: String {
        let ms = lockedForMs
        if ms > 0 {
            let seconds = Int((ms + 999) / 1000)
            return seconds >= 60 ? L("lock_wait_minutes", (seconds + 59) / 60) : L("lock_wait_seconds", seconds)
        }
        return status
    }

    private func onDigit(_ d: Character) {
        guard !checking, pin.count < pinLength else { return }
        wrong = false
        pin.append(d)
        if pin.count == pinLength { Task { await check() } }
    }

    private func check() async {
        checking = true
        let result = await model.unlock(pin: pin)
        checking = false
        pin = ""
        switch result {
        case .unlocked:
            status = ""
        case let .wrongPin(_, retryAfterMs, attemptsBeforeWipe):
            wrong = true
            status = attemptsBeforeWipe.map { LP("lock_wrong_pin_wipe", $0) } ?? L("lock_wrong_pin")
            if retryAfterMs > 0 { lockedUntil = Date().addingTimeInterval(Double(retryAfterMs) / 1000) }
        case let .lockedOut(retryAfterMs):
            lockedUntil = Date().addingTimeInterval(Double(retryAfterMs) / 1000)
        case .wiped:
            status = L("lock_wiped")
        }
    }

    private func biometric() async { _ = await model.unlockWithBiometrics() }

    private var deviceLock: some View {
        VStack(spacing: 0) {
            Spacer()
            Illustration(kind: .lockHeart).frame(width: look.sizes.illustration * 0.7, height: look.sizes.illustration * 0.7)
            Spacer().frame(height: Spacing.lg)
            Text(L("lock_title")).font(look.type.headlineMedium).accessibilityAddTraits(.isHeader)
            Spacer().frame(height: Spacing.xs)
            Text(L(DeviceAuth.isAvailable ? "lock_device_subtitle" : "lock_device_unavailable"))
                .font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.center)
            Spacer().frame(height: Spacing.xl)
            Button(L("lock_device_button")) { Task { _ = await model.unlockWithDevice() } }.buttonStyle(FilledButtonStyle(fullWidth: true))
            Spacer()
        }
        .padding(Spacing.xl)
        .background(look.colors.surface.ignoresSafeArea())
        .task { _ = await model.unlockWithDevice() }
    }
}

/// Choose PIN → confirm → Face ID (LockSetupScreen.kt).
struct LockSetupView: View {
    enum Step { case choose, confirm, biometric }
    @Environment(AppModel.self) private var model
    @Environment(\.look) private var look
    @State private var step = Step.choose
    @State private var pin = ""
    @State private var first = ""
    @State private var mismatch = false
    @State private var saving = false
    @State private var useBiometric = true

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            Illustration(kind: .lockHeart).frame(width: look.sizes.illustration * 0.6, height: look.sizes.illustration * 0.6)
            Spacer().frame(height: Spacing.lg)
            switch step {
            case .choose, .confirm: pinStep
            case .biometric: biometricStep
            }
            Spacer()
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(look.colors.surface.ignoresSafeArea())
    }

    @ViewBuilder private var pinStep: some View {
        Text(L(step == .choose ? "setup_choose_title" : "setup_confirm_title")).font(look.type.headlineMedium).multilineTextAlignment(.center)
        Spacer().frame(height: Spacing.sm)
        Text(L(mismatch ? "setup_mismatch" : "setup_pin_help"))
            .font(look.type.bodyMedium)
            .foregroundStyle(mismatch ? look.colors.error : look.colors.onSurfaceVariant)
            .multilineTextAlignment(.center)
        Spacer().frame(height: Spacing.xl)
        PinDots(count: pin.count, error: false)
        Spacer().frame(height: Spacing.xl)
        if saving {
            ProgressView().frame(height: 300)
        } else {
            PinPad(enabled: !saving, onDigit: onDigit, onBackspace: { if !pin.isEmpty { pin.removeLast() } })
        }
        if step == .choose && DeviceAuth.isAvailable {
            Spacer().frame(height: Spacing.md)
            Button(L("setup_use_device_lock")) { Task { _ = await model.chooseDeviceLock() } }.buttonStyle(TextButtonStyle())
            Text(L("setup_device_lock_help")).font(look.type.bodySmall).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.center)
        }
    }

    @ViewBuilder private var biometricStep: some View {
        Text(L("setup_biometric_title")).font(look.type.headlineMedium)
        Spacer().frame(height: Spacing.sm)
        Text(L(BiometricKeys.isAvailable ? "setup_biometric_help" : "setup_biometric_unavailable"))
            .font(look.type.bodyMedium).foregroundStyle(look.colors.onSurfaceVariant).multilineTextAlignment(.center)
        Spacer().frame(height: Spacing.xl)
        if BiometricKeys.isAvailable {
            Toggle(L("setup_biometric_switch"), isOn: $useBiometric)
                .font(look.type.bodyLarge)
                .frame(minHeight: look.sizes.touchTarget)
                .padding(.horizontal, Spacing.lg)
                .background(RoundedRectangle(cornerRadius: Radius.md).fill(look.colors.surfaceContainerLow))
            Spacer().frame(height: Spacing.xl)
        }
        Button(L("setup_finish")) { model.finishLockSetup(useBiometric: BiometricKeys.isAvailable && useBiometric) }
            .buttonStyle(FilledButtonStyle(fullWidth: true))
    }

    private func onDigit(_ d: Character) {
        guard !saving, pin.count < pinLength else { return }
        mismatch = false
        pin.append(d)
        guard pin.count == pinLength else { return }
        switch step {
        case .choose:
            first = pin
            pin = ""
            step = .confirm
        case .confirm:
            if pin == first {
                let chosen = pin
                saving = true
                Task {
                    try? await model.setPin(chosen)
                    saving = false
                    pin = ""
                    step = .biometric
                }
            } else {
                step = .choose
                first = ""
                pin = ""
                mismatch = true
            }
        case .biometric:
            break
        }
    }
}
