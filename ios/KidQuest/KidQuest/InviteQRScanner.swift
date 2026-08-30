//
//  InviteQRScanner.swift
//  KidQuest
//
//  The camera half of "koppla din enhet": payload parsing, the AVFoundation session,
//  and the state machine behind InviteQRScannerView.
//
//  Nothing in here touches the network. It produces a token string and hands it up;
//  ChildInviteLoginView.performLink does the rest, exactly as it does for a code the
//  child typed.
//

import AVFoundation
import SwiftUI
import UIKit

// MARK: - Payload

/// Vad en inbjudnings-QR faktiskt innehåller.
///
/// It is not always a bare token, and that is the whole reason this type exists. The
/// web parent's QR holds `https://<host>/invite/<token>` (see AdultDashboard.tsx),
/// while the iOS and Android parent sheets render the raw token on its own. A scanner
/// that assumed either one would reject half the family's screens as an invalid code.
///
/// The rule below is Android's, copied from ChildInviteLoginScreen.kt: everything
/// after the *last* `/invite/`, cut at the first `?`, trimmed; otherwise the whole
/// string, trimmed. Any divergence shows up as "Kontrollera koden" on a code that is
/// perfectly good — the least debuggable failure this flow has.
enum InviteQRPayload {

    /// Returnerar token ur en skannad sträng, eller `nil` om det inte finns någon.
    static func token(from raw: String) -> String? {
        let marker = "/invite/"
        let candidate: String

        if let marked = raw.range(of: marker, options: .backwards) {
            // substringAfterLast("/invite/").trim().substringBefore("?").trim()
            let afterMarker = raw[marked.upperBound...]
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let beforeQuery = afterMarker.split(
                separator: "?",
                maxSplits: 1,
                omittingEmptySubsequences: false
            ).first.map(String.init) ?? afterMarker
            candidate = beforeQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        } else {
            candidate = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        }

        // Android drops blanks rather than sending them, and so do we: a blank token
        // comes back as the same "kontrollera koden" the child just failed on, with
        // nothing for them to check.
        return candidate.isEmpty ? nil : candidate
    }
}

// MARK: - Why the camera might not be usable

/// Varför kameran inte går att använda just nu.
///
/// Every one of these happens to somebody on a child's phone, so each gets its own
/// case and its own sentence on screen rather than one shared "något gick fel".
enum InviteScannerBlock: Equatable {
    /// Enheten har ingen kamera alls — simulatorn, i praktiken.
    case noCamera
    /// Någon har svarat nej på systemfrågan. Bara Inställningar kan ändra det.
    case permissionDenied
    /// Kameran är avstängd av Skärmtid eller MDM — inte barnets att ändra.
    case permissionRestricted
    /// Kameran finns och är tillåten, men sessionen gick inte att starta.
    case sessionFailed
}

/// Bakre kameran om den finns, annars vilken som helst.
///
/// `nonisolated` because the session's configuration runs on its own queue; the file
/// otherwise defaults to MainActor (SWIFT_DEFAULT_ACTOR_ISOLATION).
nonisolated func inviteScannerCaptureDevice() -> AVCaptureDevice? {
    if let back = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) {
        return back
    }
    // An iPad or a Mac may only have a front camera, and a code held up to it scans
    // just as well.
    return AVCaptureDevice.default(for: .video)
}

// MARK: - Session

/// Äger AVFoundation-objekten och kön de körs på.
///
/// Deliberately `nonisolated`: `startRunning()` blocks until the camera is up — long
/// enough to stutter the UI if done on the main thread — and Apple's guidance is to
/// drive configuration and start/stop from one dedicated serial queue. Everything
/// mutable in here is touched only on `queue`, which is what the unchecked Sendable
/// conformance stands on. Results hop back to the main actor before anyone sees them.
nonisolated final class InviteQRCaptureSession: NSObject, @unchecked Sendable {

    let session = AVCaptureSession()

    /// Vem som får veta. Weak so a dismissed sheet is not kept alive by the camera.
    /// A `@MainActor` class is implicitly Sendable, so this crosses queues cleanly.
    weak var listener: InviteQRScannerModel?

    private let queue = DispatchQueue(label: "se.kidquest.invite-qr.session")
    private let metadataOutput = AVCaptureMetadataOutput()

    /// Sätts en gång, bara på `queue`. Se `metadataOutput(_:didOutput:from:)`.
    private var hasDelivered = false

    /// Bygger sessionen och startar den. Säker att kalla mer än en gång.
    func start() {
        queue.async { [weak self] in
            guard let self, !self.hasDelivered else { return }
            guard !self.session.isRunning else { return }

            if self.session.inputs.isEmpty && !self.configure() {
                self.deliverFailure()
                return
            }

            self.session.startRunning()
        }
    }

    /// Stoppar sessionen. Säker att kalla mer än en gång, från vilken tråd som helst.
    func stop() {
        queue.async { [weak self] in
            guard let self, self.session.isRunning else { return }
            self.session.stopRunning()
        }
    }

    /// Kör bara på `queue`.
    private func configure() -> Bool {
        guard let device = inviteScannerCaptureDevice() else { return false }

        let input: AVCaptureDeviceInput
        do {
            input = try AVCaptureDeviceInput(device: device)
        } catch {
            // A camera that exists but will not open: held by another app, or seized
            // by the system. Nothing here to retry.
            return false
        }

        session.beginConfiguration()
        defer { session.commitConfiguration() }

        guard session.canAddInput(input) else { return false }
        session.addInput(input)

        guard session.canAddOutput(metadataOutput) else { return false }
        session.addOutput(metadataOutput)

        metadataOutput.setMetadataObjectsDelegate(self, queue: queue)
        // Order matters: `availableMetadataObjectTypes` is only populated once the
        // output is attached to a session, so asking any earlier silently yields
        // nothing and the scanner would look at QR codes and see none of them.
        metadataOutput.metadataObjectTypes = metadataOutput.availableMetadataObjectTypes.contains(.qr)
            ? [.qr]
            : []

        // Only QR — a barcode on a cereal box should not be mistaken for a family.
        return !metadataOutput.metadataObjectTypes.isEmpty
    }

    /// Kör bara på `queue`.
    private func deliverFailure() {
        guard !hasDelivered else { return }
        hasDelivered = true
        Task { @MainActor [weak listener] in
            listener?.handle(block: .sessionFailed)
        }
    }
}

// `nonisolated` on the extension too: under SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor
// an extension is MainActor even when the type is not, which would make the delegate
// conformance itself main-actor-isolated -- and AVFoundation calls it on our queue.
nonisolated extension InviteQRCaptureSession: AVCaptureMetadataOutputObjectsDelegate {

    /// Anropas på `queue` — inte på huvudtråden.
    nonisolated func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !hasDelivered else { return }

        let code = metadataObjects
            .compactMap { $0 as? AVMetadataMachineReadableCodeObject }
            .first { $0.type == .qr }?
            .stringValue

        guard let code, !code.isEmpty else { return }

        // Stop before delivering, not after. The output keeps firing for as long as
        // the code stays in frame — several times a second — and a second delivery
        // would spend the same invite token twice. `hasDelivered` is the belt to this
        // braces: a frame already in flight can still land here after stopRunning().
        hasDelivered = true
        session.stopRunning()

        Task { @MainActor [weak listener] in
            listener?.handle(scanned: code)
        }
    }
}

// MARK: - State machine

/// Vad skanningsarket visar just nu.
@MainActor
@Observable
final class InviteQRScannerModel {

    enum Phase: Equatable {
        /// Innan vi vet något — ett ögonblick, medan enhet och tillstånd kollas.
        case preparing
        /// Kameran är igång.
        case scanning
        /// Kameran går inte att använda, av ett av fyra skäl.
        case blocked(InviteScannerBlock)
        /// En kod lästes, men det fanns ingen token i den.
        case unreadable
    }

    private(set) var phase: Phase = .preparing

    /// Bumpas när kameran startas om, så SwiftUI river och bygger förhandsvisningen.
    private(set) var sessionGeneration: Int = 0

    /// Sätts en gång, när en giltig token lästs. Vyn reagerar och stänger arket.
    private(set) var scannedToken: String?

    private(set) var session = InviteQRCaptureSession()

    init() {
        session.listener = self
    }

    /// Kollar enhet och tillstånd, och frågar barnet om lov om det behövs.
    ///
    /// Device availability is checked *before* the permission prompt, deliberately.
    /// Asking a child for the most invasive permission in the app and then showing
    /// them a black rectangle because there is no camera is the worst of both — and
    /// on the simulator that is the only path there is.
    ///
    /// This runs when the sheet appears, and the sheet only appears when the child
    /// taps "Skanna QR-kod". Nothing asks for the camera at launch.
    func prepare() async {
        guard inviteScannerCaptureDevice() != nil else {
            phase = .blocked(.noCamera)
            return
        }

        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            phase = .scanning
        case .notDetermined:
            let granted = await AVCaptureDevice.requestAccess(for: .video)
            phase = granted ? .scanning : .blocked(.permissionDenied)
        case .denied:
            phase = .blocked(.permissionDenied)
        case .restricted:
            phase = .blocked(.permissionRestricted)
        @unknown default:
            // A status this build has no name for is not something to guess at; treat
            // it as "we cannot use the camera" rather than opening a dead preview.
            phase = .blocked(.permissionDenied)
        }
    }

    /// Kallas från kamerakön via huvudtråden.
    func handle(scanned raw: String) {
        guard scannedToken == nil else { return }

        if let token = InviteQRPayload.token(from: raw) {
            scannedToken = token
        } else {
            // A QR that is not one of ours — a Wi-Fi code, a poster, a receipt. Say so
            // and let them aim again rather than sending nonsense to the server.
            phase = .unreadable
        }
    }

    /// Kallas från kamerakön via huvudtråden.
    func handle(block: InviteScannerBlock) {
        phase = .blocked(block)
    }

    /// Ny session efter en oläslig kod. Den gamla har redan levererat och är låst.
    func restart() {
        session.stop()
        session.listener = nil

        let fresh = InviteQRCaptureSession()
        fresh.listener = self
        session = fresh
        sessionGeneration += 1
        phase = .scanning
    }

    func stop() {
        session.stop()
    }
}

// MARK: - Preview layer

/// Den levande bilden från kameran.
private final class CameraPreviewUIView: UIView {

    /// Låter vyns egen layer *vara* preview-lagret, så bilden aldrig hamnar ur takt
    /// med vyns ram vid rotation eller när arket ändrar höjd.
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }

    private var previewLayer: AVCaptureVideoPreviewLayer? {
        layer as? AVCaptureVideoPreviewLayer
    }

    func attach(session: AVCaptureSession) {
        previewLayer?.session = session
        previewLayer?.videoGravity = .resizeAspectFill
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        updateRotation()
    }

    /// Håller bilden rättvänd.
    ///
    /// Detection itself is orientation-agnostic — it runs on the raw buffer, so a
    /// sideways preview would still scan — but a child holding a phone up to a
    /// parent's screen needs the picture to match the room, or they cannot aim.
    private func updateRotation() {
        guard let connection = previewLayer?.connection else { return }
        let orientation = window?.windowScene?.effectiveGeometry.interfaceOrientation ?? .portrait

        // 0° is the camera's native landscape; portrait is a quarter turn from it.
        let angle: CGFloat
        switch orientation {
        case .landscapeRight: angle = 0
        case .portraitUpsideDown: angle = 270
        case .landscapeLeft: angle = 180
        default: angle = 90
        }

        if connection.isVideoRotationAngleSupported(angle) {
            connection.videoRotationAngle = angle
        }
    }
}

/// SwiftUI-omslag för kamerabilden.
struct InviteQRCameraPreview: UIViewRepresentable {

    let session: InviteQRCaptureSession

    func makeUIView(context: Context) -> UIView {
        let view = CameraPreviewUIView()
        view.backgroundColor = .black
        view.attach(session: session.session)
        session.start()
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        // Nothing to push down: the session drives itself and the layer is the view.
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        // The view going away is the honest signal to switch the camera off: the sheet
        // was dismissed, or swiped down without ever scanning anything.
        coordinator.session.stop()
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(session: session)
    }

    /// Håller sessionen vid liv tills `dismantleUIView` hunnit stoppa den.
    final class Coordinator {
        let session: InviteQRCaptureSession

        init(session: InviteQRCaptureSession) {
            self.session = session
        }
    }
}
