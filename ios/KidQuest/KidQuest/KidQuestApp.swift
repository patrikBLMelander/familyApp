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
            ContentView()
                .onAppear {
                    TokenStoreIOS.shared.load()
                }
        }
    }
}
