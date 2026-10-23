import Foundation

struct ModelChoice: Identifiable {
    var id: String { "\(providerId)|\(model)" }
    var providerId: String
    var providerName: String
    var model: String
}

struct PendingConfirmation: Identifiable {
    var id: String { callId }
    var callId: String
    var title: String
    var option: Any
}

func displayText(_ value: Any?) -> String {
    switch value {
    case let text as String:
        return text
    case let object as [String: Any]:
        if let text = object["content"] as? String, !text.isEmpty { return text }
        if let text = object["text"] as? String, !text.isEmpty { return text }
        return displayText(object["content"])
    case let list as [Any]:
        return list.map { displayText($0) }.filter { !$0.isEmpty }.joined(separator: "\n")
    default:
        return ""
    }
}

func conversationRows(_ data: Data) -> [(String, String)] {
    array(data, preferred: "items").compactMap { item in
        guard let id = item["conversation_id"] as? String, !id.isEmpty else { return nil }
        let name = (item["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "未命名对话"
        return (id, name)
    }
}

func messageLines(_ data: Data) -> [(Bool, String)] {
    array(data, preferred: "items").compactMap { item in
        let text = displayText(item["content"])
        guard !text.isEmpty else { return nil }
        return ((item["type"] as? String) == "user", text)
    }
}

func modelChoices(_ data: Data) -> [ModelChoice] {
    array(data, preferred: nil).flatMap { item -> [ModelChoice] in
        let id = (item["provider_id"] as? String) ?? ""
        let name = (item["name"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "模型"
        let models = item["models"] as? [String] ?? []
        return models.filter { !id.isEmpty && !$0.isEmpty }.map { ModelChoice(providerId: id, providerName: name, model: $0) }
    }
}

func confirmationLines(_ data: Data) -> [PendingConfirmation] {
    array(data, preferred: nil).compactMap { item in
        guard let callId = (item["call_id"] as? String) ?? (item["callId"] as? String), !callId.isEmpty else { return nil }
        let title = (item["description"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? (item["title"] as? String).flatMap { $0.isEmpty ? nil : $0 }
            ?? "需要确认"
        let option = (item["options"] as? [[String: Any]])?.first?["value"] ?? "allow"
        return PendingConfirmation(callId: callId, title: title, option: option)
    }
}

func libraryRows(_ data: Data) -> [String] {
    let rows = array(data, preferred: "items").compactMap { item -> String? in
        let name = (item["name"] as? String) ?? (item["title"] as? String) ?? (item["id"] as? String) ?? ""
        return name.isEmpty ? nil : name
    }
    return rows
}

func latestMessageId(_ data: Data) -> String? {
    guard let item = array(data, preferred: "items").last else { return nil }
    let id = (item["msg_id"] as? String) ?? (item["message_id"] as? String) ?? ""
    return id.isEmpty ? nil : id
}

private func array(_ data: Data, preferred: String?) -> [[String: Any]] {
    guard let root = try? JSONSerialization.jsonObject(with: data) else { return [] }
    let payload: Any = (root as? [String: Any])?["data"] ?? root
    if let list = payload as? [[String: Any]] { return list }
    guard let object = payload as? [String: Any] else { return [] }
    if let key = preferred, let list = object[key] as? [[String: Any]] { return list }
    if let list = object["items"] as? [[String: Any]] { return list }
    if let list = object["list"] as? [[String: Any]] { return list }
    return [object]
}
