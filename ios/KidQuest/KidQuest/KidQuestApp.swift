//
//  KidQuestApp.swift
//  KidQuest
//
//  Created by Patrik Melander on 2026-03-03.
//

import SwiftUI

@main
struct KidQuestApp: App {
    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if let harness = ScreenHarness.requested {
                harness.view
            } else {
                root
            }
            #else
            root
            #endif
        }
    }

    private var root: some View {
        ContentView()
            .onAppear {
                TokenStoreIOS.shared.load()
            }
    }
}

#if DEBUG
/// Renders one screen with sample data, chosen by launch environment.
///
/// The Android app can be driven from the command line — launch it, tap a coordinate,
/// photograph the result — which is what makes its screenshots a fair reference. The
/// iOS simulator will hand over a screenshot but takes no input, so a screen deep
/// behind a login cannot be reached to be looked at at all.
///
/// This is the way in: `simctl launch --console KQ_SCREEN=dashboard` renders that
/// screen against fixtures, with no network and no session. Debug builds only, and
/// absent from anything shipped.
///
/// It is deliberately fixtures rather than a seeded real session. A session would mean
/// putting a working device token somewhere it could be read, and a token is a bearer
/// credential for a real family.
enum ScreenHarness {

    struct Entry {
        let view: AnyView
    }

    static var requested: Entry? {
        guard let name = ProcessInfo.processInfo.environment["KQ_SCREEN"] else { return nil }
        return entry(named: name)
    }

    private static func entry(named name: String) -> Entry? {
        // Season and mode ride along so a screen can be photographed in any of the
        // eight palettes without waiting for the calendar to reach that month.
        let env = ProcessInfo.processInfo.environment
        let season = env["KQ_SEASON"] ?? SeasonTheme.currentSeason()
        let dark = (env["KQ_DARK"] ?? "0") == "1"
        let palette = SeasonTheme.paletteFor(season, dark: dark)

        // Applied from out here rather than inside the screen, so the production view
        // keeps no knowledge of being photographed and every future screen inherits it.
        // Without this a critic can only ever see the top of a screen: the simulator
        // takes no touch input, so nothing below the fold can be reached at all.
        let anchor: UnitPoint? = (env["KQ_SCROLL"] == "bottom") ? .bottom : nil

        switch name {
        // The four screens a family sees before there is a session at all. Each one
        // is reached in a real run by tapping the one before it, which the simulator
        // cannot do, so each gets its own name here.
        case "welcome":
            return Entry(view: AnyView(
                WelcomeView.fixture()
                    .environment(\.seasonPalette, palette)
                    .preferredColorScheme(dark ? .dark : .light)
                    .defaultScrollAnchor(anchor, for: .initialOffset)
            ))
        case "login":
            return Entry(view: AnyView(
                AuthView.fixture()
                    .environment(\.seasonPalette, palette)
                    .preferredColorScheme(dark ? .dark : .light)
                    .defaultScrollAnchor(anchor, for: .initialOffset)
            ))
        case "register":
            return Entry(view: AnyView(
                RegisterView.fixture()
                    .environment(\.seasonPalette, palette)
                    .preferredColorScheme(dark ? .dark : .light)
                    .defaultScrollAnchor(anchor, for: .initialOffset)
            ))
        case "childinvite":
            return Entry(view: AnyView(
                ChildInviteLoginView.fixture()
                    .environment(\.seasonPalette, palette)
                    .preferredColorScheme(dark ? .dark : .light)
                    .defaultScrollAnchor(anchor, for: .initialOffset)
            ))
        case "dashboard", "dashboard-nopets":
            return Entry(view: AnyView(
                AdultDashboardView.fixture(pets: name == "dashboard")
                    .environment(\.seasonPalette, palette)
                    .preferredColorScheme(dark ? .dark : .light)
                    .defaultScrollAnchor(anchor, for: .initialOffset)
            ))
        // Two entries rather than one, for the same reason the harness exists at all:
        // the Vecka tab cannot be tapped, so without a way to open the screen on it
        // that half of the screen can never be looked at.
        case "childtasks", "childtasks-week":
            return Entry(view: AnyView(
                ChildTasksView.fixture(tab: name == "childtasks-week" ? .week : .today)
                    .environment(\.seasonPalette, palette)
                    .preferredColorScheme(dark ? .dark : .light)
                    .defaultScrollAnchor(anchor, for: .initialOffset)
            ))
        default:
            return nil
        }
    }
}
#endif
