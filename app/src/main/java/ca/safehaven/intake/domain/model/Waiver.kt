package ca.safehaven.intake.domain.model

/**
 * The legal wording of Safe Haven for Animals' liability waiver, copied word for word from the
 * client's paper form (`docs/client/SHFA-WaiverForm.pdf`, not in git). Only spacing and one
 * split word ("ma de") were tidied.
 *
 * The form screen and the PDF both read from here, so the two can't drift apart. If the client
 * changes the wording, update this file **and** bump [VERSION]; the version is saved with every
 * record so staff can tell which wording a visitor signed.
 */
object Waiver {
    const val VERSION = "SHFA-2026-11"

    const val WARNING =
        "BY SIGNING THIS AGREEMENT, YOU ARE GIVING UP CERTAIN LEGAL RIGHTS, INCLUDING THE " +
            "RIGHT TO RECOVER DAMAGES IN CASE OF INJURY, DEATH, OR PROPERTY DAMAGE, ARISING OUT " +
            "OF YOUR PRESENCE AND/OR PARTICIPATION AT SAFE HAVEN FOR ANIMALS, INCLUDING INJURY, " +
            "DEATH, OR PROPERTY DAMAGE ARISING OUT OF THE NEGLIGENCE OF SAFE HAVEN FOR ANIMALS."

    const val READ_CAREFULLY =
        "READ THIS AGREEMENT CAREFULLY BEFORE SIGNING IT. YOUR SIGNATURE INDICATES YOUR " +
            "UNDERSTANDING OF AND AGREEMENT TO ITS TERMS."

    const val ACKNOWLEDGEMENT =
        "By signing this form, I hereby acknowledge that I have familiarized myself with the " +
            "activities that I will be allowed to participate in, and that I do hereby " +
            "acknowledge and agree that I will participate in these activities without " +
            "restriction or limitation."

    val RISKS: List<String> = listOf(
        "Bites or scratches from animals.",
        "Scratches or other injury from cages.",
        "Scratches or other injury from grooming tools.",
        "Allergic reactions to animals, cleaning agents, chemicals, or other allergens.",
        "Tripping or slipping on wet floor, cats, toys, etc.",
        "Slipping, falling, or otherwise being injured anywhere on the premises.",
    )

    const val RELEASE =
        "I hereby specifically forever waive and release Safe Haven for Animals and its " +
            "principals, agents, and volunteers from any liability for injury arising out of the " +
            "inherent risks from working in an animal shelter, as set forth in a non-exclusive " +
            "fashion previously herein, as well as from the active negligence of Safe Haven for " +
            "Animals, its principals, and agents."

    const val INITIALS_INTRO =
        "By placing my initials beside each of the paragraphs below, I hereby acknowledge my " +
            "complete understanding of the information provided; by placing my initials beside " +
            "each of the paragraphs below, I do hereby consent to my participation in the " +
            "activities at Safe Haven for Animals, without restriction, without liability to Safe " +
            "Haven for Animals, its principals, or agents, and with full knowledge and " +
            "understanding of the disclosures herein."

    const val SIGNATURE_NOTE =
        "*Adult Participant’s Signature or Parent/Guardian Signature (if child is under 18)"
}

/** The six paragraphs the visitor initials, in the paper form's order. */
enum class WaiverClause(val text: String) {
    NO_MEDICATION(
        "No medications of any kind, whether prescription or non-prescription, will be given " +
            "to you while at Safe Haven for Animals.",
    ),
    GOOD_HEALTH(
        "I am in good health and do not present a health risk to others who may be present at " +
            "Safe Haven for Animals.",
    ),
    NO_COMMUNICABLE_DISEASE(
        "I do not have any communicable diseases, including, but not limited to Mumps, " +
            "Measles, Chicken Pox, Hepatitis, HIV/Aids.",
    ),
    INDEMNIFY(
        "I agree to indemnify, save, and hold harmless Safe Haven for Animals and its " +
            "principals, agents, and volunteers from and against any loss, liability, damage, " +
            "attorneys' fees, or costs that they may incur arising out of or in any way " +
            "connected with either my presence or participation at Safe Haven for Animals or " +
            "any acts or omissions of Safe Haven for Animals' principals, agents, or volunteers.",
    ),
    NO_NURSE(
        "I acknowledge that there will not be a nurse on the premises and Safe Haven for " +
            "Animals and its principals, agents and volunteers bear no responsibility for me.",
    ),
    FACTS_TRUE(
        "By submitting this application, I affirm that the facts set forth in it are true and " +
            "complete. I understand that if I am accepted as a volunteer, any false statements, " +
            "omissions, or other misrepresentations made by me on this application may result " +
            "in immediate dismissal.",
    ),
}
