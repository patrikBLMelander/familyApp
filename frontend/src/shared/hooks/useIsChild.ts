import { useState, useEffect } from "react";
import { getMemberByDeviceToken, FamilyMemberResponse } from "../api/familyMembers";

export function useIsChild() {
  const [isChild, setIsChild] = useState<boolean | null>(null);
  const [childMember, setChildMember] = useState<FamilyMemberResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const checkChildStatus = async () => {
      try {
        const deviceToken = localStorage.getItem("deviceToken");
        if (!deviceToken) {
          setIsChild(false);
          setChildMember(null);
          setLoading(false);
          return;
        }

        // Try to get member by device token
        const member = await getMemberByDeviceToken(deviceToken);
        // Set as child if role is CHILD or ASSISTANT (both can have pets)
        if (member.role === "CHILD" || member.role === "ASSISTANT") {
          setIsChild(true);
          setChildMember(member);
        } else {
          // Parent logged in - not a child
          setIsChild(false);
          setChildMember(null);
        }
      } catch {
        // Not a valid child token
        setIsChild(false);
        setChildMember(null);
        // Clear invalid token
        localStorage.removeItem("deviceToken");
      } finally {
        setLoading(false);
      }
    };

    void checkChildStatus();
  }, []);

  return { isChild: isChild ?? false, childMember, loading };
}

